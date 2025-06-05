/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.ElementKind;

// TODO(pm): this is probably very slow. use VisitorState to cache the results so the same method is not scanned
//  multiple times. Check if this blows up memory. But AFAICT we do this for SafetyAnalysis, so it should be fine.
// TODO(pm): need control-flow analysis to see when the exception would be caught.
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "TODO")
public final class UnassociatedEndpointError extends BugChecker implements BugChecker.MethodTreeMatcher {
    private static final String GENERATED_ANNOTATION = "javax.annotation.processing.Generated";
    private static final String UNDERTOW_SERVICE_INTERFACE_GENERATOR =
            "com.palantir.conjure.java.services.UndertowServiceInterfaceGenerator";
    private static final String ENDPOINT_SERVICE_EXCEPTION =
            "com.palantir.conjure.java.api.errors.EndpointServiceException";
    private static final Supplier<Type> endpointServiceExceptionSupplier =
            VisitorState.memoize(state -> state.getTypeFromString(ENDPOINT_SERVICE_EXCEPTION));

    //    private static UnderlyingAST createAst(TreePath path) {
    //        Tree tree = path.getLeaf();
    //        ClassTree enclosingClass = TreePathUtil.enclosingClass(path);
    //        if (tree instanceof MethodTree) {
    //            return new UnderlyingAST.CFGMethod((MethodTree) tree, enclosingClass);
    //        }
    //        if (tree instanceof LambdaExpressionTree) {
    //            return new UnderlyingAST.CFGLambda(
    //                    (LambdaExpressionTree) tree, enclosingClass, TreePathUtil.enclosingMethod(path));
    //        }
    //        return new CFGStatement(tree, enclosingClass);
    //    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (!isConjureEndpoint(tree, state)) {
            return Description.NO_MATCH;
        }
        // Scan the method body for EndpointServiceExceptions
        EndpointAssociatedExceptionScanner scanner = new EndpointAssociatedExceptionScanner();
        scanner.scan(state.getPath().getCompilationUnit(), null);

        //        JavacProcessingEnvironment javacEnv = JavacProcessingEnvironment.instance(state.context);
        //        TreePath methodDef = Trees.instance(javacEnv).getPath(ASTHelpers.getSymbol(tree));
        //        UnderlyingAST ast = createAst(methodDef);
        //        ControlFlowGraph cfg = CFGBuilder.build(state.getPath().getCompilationUnit(), ast, false, false,
        // javacEnv);

        Set<String> thrownExceptions = scanner.getThrownExceptions();
        if (thrownExceptions.isEmpty()) {
            return Description.NO_MATCH;
        }

        // TODO(pm): need to filter out exceptions that have already been associated with the endpoint. But let's just
        //  not filter for testing.
        return buildDescription(tree)
                .setMessage(String.format(
                        "Endpoint throws unassociated exceptions: %s", String.join(", ", thrownExceptions)))
                .build();
    }

    @SuppressWarnings("VoidUsed")
    private static final class EndpointAssociatedExceptionScanner extends TreePathScanner<Void, VisitorState> {
        private final Set<String> thrownExceptions = new HashSet<>();

        private EndpointAssociatedExceptionScanner() {}

        public Set<String> getThrownExceptions() {
            return Collections.unmodifiableSet(thrownExceptions);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocationTree, VisitorState visitorState) {
            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(invocationTree);

            if (methodSymbol != null) {
                // This won't catch the case where the exceptions are unchecked.
                for (Type declaredThrownType : methodSymbol.getThrownTypes()) {
                    if (visitorState
                            .getTypes()
                            .isSubtype(declaredThrownType, endpointServiceExceptionSupplier.get(visitorState))) {
                        // This method call declares it might throw the target exception (or a subtype).
                        // Now, you need to check if this exception is caught by an enclosing try-catch block.
                        if (!isExceptionCaught(visitorState.getPath(), declaredThrownType, visitorState)) {
                            // Exception is thrown and not caught by an obvious handler.
                            // Report a match or set a flag.
                        }
                    }
                }
            }
            return super.visitMethodInvocation(invocationTree, visitorState);
        }

        @Override
        public Void visitThrow(ThrowTree throwTree, VisitorState state) {
            Type thrownExpressionType = ASTHelpers.getType(throwTree.getExpression());
            Type targetExceptionType = endpointServiceExceptionSupplier.get(state);

            if (targetExceptionType != null
                    // What is this?
                    && !targetExceptionType.isErroneous()
                    && thrownExpressionType != null
                    && state.getTypes().isSubtype(thrownExpressionType, targetExceptionType)) {

                if (!isExceptionCaught(getCurrentPath(), thrownExpressionType, state)) {
                    if (thrownExpressionType.tsym != null) {
                        thrownExceptions.add(
                                thrownExpressionType.tsym.getQualifiedName().toString());
                    } else {
                        thrownExceptions.add(thrownExpressionType.toString());
                    }
                }
            }
            return super.visitThrow(throwTree, state);
        }

        //        @Override
        //        @SuppressWarnings("BanSystemOut")
        //        public Void visitThrow(ThrowTree node, VisitorState unused) {
        //            // Check if the thrown exception is an unchecked subtype CheckedServiceException
        //            Type thrownType = ASTHelpers.getType(node.getExpression());
        //            if (thrownType == null) {
        //                return super.visitThrow(node, unused);
        //            }
        //            if (!(thrownType.tsym instanceof ClassSymbol thrownClassSymbol)) {
        //                return super.visitThrow(node, unused);
        //            }
        //
        //            Type superclass = thrownClassSymbol.getSuperclass();
        //            if (superclass != null
        //                    && superclass.tsym.getQualifiedName().toString().contains(ENDPOINT_SERVICE_EXCEPTION)) {
        //                // System.out.println("Found exception thrown: " + thrownClassSymbol.getQualifiedName());
        //                thrownExceptions.add(thrownClassSymbol.getQualifiedName().toString());
        //            }
        //
        //            return super.visitThrow(node, unused);
        //        }
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static boolean isExceptionCaught(TreePath path, Type thrownType, VisitorState state) {
        TreePath currentPath = path;
        while (currentPath != null) {
            TryTree tryTree = ASTHelpers.findEnclosingNode(currentPath, TryTree.class);
            if (tryTree == null) {
                // No enclosing try-catch block from this point upwards in this method
                return false;
            }

            // Check if this tryTree is still within the same method body or lambda body
            // This check is important if isExceptionCaught is called from a deep AST path.
            MethodTree enclosingMethod = ASTHelpers.findEnclosingNode(currentPath, MethodTree.class);
            LambdaExpressionTree enclosingLambda =
                    ASTHelpers.findEnclosingNode(currentPath, LambdaExpressionTree.class);

            MethodTree originalMethod = ASTHelpers.findEnclosingNode(path, MethodTree.class);
            LambdaExpressionTree originalLambda = ASTHelpers.findEnclosingNode(path, LambdaExpressionTree.class);

            if ((enclosingMethod == null || !enclosingMethod.equals(originalMethod))
                    && (enclosingLambda == null || !enclosingLambda.equals(originalLambda))) {
                // The found TryTree is outside the current method/lambda scope of the throw.
                return false;
            }

            Type baseType = endpointServiceExceptionSupplier.get(state);
            for (CatchTree catchTree : tryTree.getCatches()) {
                Type caughtType = ASTHelpers.getType(catchTree.getParameter().getType());
                if (caughtType != null && state.getTypes().isSubtype(thrownType, caughtType)) {
                    // The specific thrownType is potentially caught.
                    // Check if the catch block re-throws it or a compatible EndpointServiceException.
                    CatchBlockThrowsScanner catchScanner = new CatchBlockThrowsScanner(state, thrownType, baseType);
                    catchScanner.scan(new TreePath(new TreePath(currentPath, tryTree), catchTree.getBlock()), state);
                    if (!catchScanner.mightRethrow()) {
                        return true; // Caught and not re-thrown by this catch block
                    }
                    // If it might be re-thrown by this catch, it's not truly handled at this level.
                    // Continue to see if an outer try-catch handles the re-thrown exception.
                }
            }

            // If not caught by this tryTree's catches, or caught and rethrown,
            // move to the parent of the tryTree to check for outer try-catch blocks.
            currentPath = currentPath.getParentPath();
        }
        return false; // Not caught by any enclosing try-catch within the method, or caught and re-thrown upwards
    }

    // Scans a catch block to see if it re-throws the caught exception or another EndpointServiceException
    private static class CatchBlockThrowsScanner extends TreePathScanner<Void, VisitorState> {
        private final VisitorState analysisState;
        private final Type originallyCaughtType;
        private final Type endpointServiceExceptionBaseType;
        private boolean mightRethrow = false;

        CatchBlockThrowsScanner(
                VisitorState analysisState, Type originallyCaughtType, Type endpointServiceExceptionBaseType) {
            this.analysisState = analysisState;
            this.originallyCaughtType = originallyCaughtType;
            this.endpointServiceExceptionBaseType = endpointServiceExceptionBaseType;
        }

        public boolean mightRethrow() {
            return mightRethrow;
        }

        @Override
        public Void visitThrow(ThrowTree throwTree, VisitorState state) {
            if (mightRethrow) {
                return super.visitThrow(throwTree, state);
            }

            Type rethrownType = ASTHelpers.getType(throwTree.getExpression());
            if (rethrownType != null) {
                // Check if it re-throws the originallyCaughtType (or a subtype)
                if (analysisState.getTypes().isSubtype(rethrownType, originallyCaughtType)) {
                    mightRethrow = true;
                } else if (endpointServiceExceptionBaseType != null
                        && !endpointServiceExceptionBaseType.isErroneous()
                        && analysisState.getTypes().isSubtype(rethrownType, endpointServiceExceptionBaseType)) {
                    // Check if it throws any EndpointServiceException (or a subtype)
                    mightRethrow = true;
                }
            }
            return super.visitThrow(throwTree, state);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocationTree, VisitorState state) {
            if (mightRethrow) {
                return super.visitMethodInvocation(invocationTree, state);
            }

            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(invocationTree);
            if (methodSymbol != null) {
                // Check declared thrown types
                for (Type declaredThrownType : methodSymbol.getThrownTypes()) {
                    if (analysisState.getTypes().isSubtype(declaredThrownType, originallyCaughtType)) {
                        mightRethrow = true;
                        return super.visitMethodInvocation(invocationTree, state);
                    }
                    if (endpointServiceExceptionBaseType != null
                            && !endpointServiceExceptionBaseType.isErroneous()
                            && analysisState
                                    .getTypes()
                                    .isSubtype(declaredThrownType, endpointServiceExceptionBaseType)) {
                        mightRethrow = true;
                        return super.visitMethodInvocation(invocationTree, state);
                    }
                }
                // Detecting undeclared unchecked exceptions from method calls here is hard,
                // similar to the main scanner. For simplicity, this catch block scanner
                // primarily focuses on direct throws and declared throws within the catch.
            }
            return super.visitMethodInvocation(invocationTree, state);
        }
    }

    private boolean isOverrideMethod(MethodTree tree, VisitorState state) {
        return !ASTHelpers.findSuperMethods(ASTHelpers.getSymbol(tree), state.getTypes())
                .isEmpty();
    }

    /**
     * Conjure endpoint methods are overrides of methods defined in a Conjure-generated Undertow service interface.
     */
    @SuppressWarnings("BanSystemOut")
    private boolean isConjureEndpoint(MethodTree tree, VisitorState state) {
        ClassSymbol enclosingClass = ASTHelpers.getSymbol(tree).enclClass();
        if (enclosingClass == null) {
            return false;
        }

        if (!isOverrideMethod(tree, state)) {
            return false;
        }

        for (Type interfaceSymbol : enclosingClass.getInterfaces()) {
            if (!(interfaceSymbol.tsym instanceof ClassSymbol)) {
                continue;
            }
            return interfaceContainsMethodWithName(
                    interfaceSymbol, tree.getName().toString());
            //            for (AnnotationMirror annotation : interfaceSymbol.tsym.getAnnotationMirrors()) {
            //                System.out.println("Found annotation: " + annotation.getAnnotationType());
            // if (isGeneratedByUndertowServiceInterfaceGenerator(annotation)) {

            // }
            // }
        }
        return false;
    }

    @SuppressWarnings("BanSystemOut")
    private boolean interfaceContainsMethodWithName(Type interfaceType, String methodName) {
        if (!(interfaceType.tsym instanceof ClassSymbol interfaceSymbol)) {
            return false;
        }
        for (Symbol method : ASTHelpers.getEnclosedElements(interfaceSymbol)) {
            if (method.getKind() == ElementKind.METHOD
                    && method.getSimpleName().toString().equals(methodName)) {
                // System.out.println("Found conjure endpoint method: " + method.getSimpleName());
                return true;
            }
        }
        return false;
    }

    // TODO(pm): problem: generated annotations have source retention, so we can't use them here.
    //  How do we identify a Conjure generated undertow interface?
    //    private boolean isGeneratedByUndertowServiceInterfaceGenerator(AnnotationMirror annotation) {
    //        return annotation.getAnnotationType().toString().equals(GENERATED_ANNOTATION)
    //                && annotation.getElementValues().values().stream()
    //                        .anyMatch(value ->
    // value.getValue().toString().contains(UNDERTOW_SERVICE_INTERFACE_GENERATOR));
    //    }
}

// @AutoService(BugChecker.class)
// @BugPattern(
//        summary = "Ensure that methods annotated with @Endpoint are associated with an exception that extends"
//                + " EndpointServiceException.",
//        severity = BugPattern.SeverityLevel.ERROR)
// public final class UnassociatedEndpointError extends BugChecker implements BugChecker.MethodTreeMatcher {
//
//    private static final String ENDPOINT_ANNOTATION = "com.palantir.conjure.java.lib.internal.Endpoint";
//    static final String ENDPOINT_SERVICE_EXCEPTION = "com.palantir.conjure.java.api.errors.EndpointServiceException";
//    private static final Matcher<Tree> IS_ENDPOINT_SERVICE_EXCEPTION_SUBTYPE =
// Matchers.isSubtypeOf(ENDPOINT_SERVICE_EXCEPTION);
//
//    @Override
//    public Description matchMethod(MethodTree tree, VisitorState state) {
//        List<? extends AnnotationTree> annotations = tree.getModifiers().getAnnotations();
//        boolean isEndpoint = annotations.stream()
//                .anyMatch(annotation -> ASTHelpers.isSameType(
//                        ASTHelpers.getType(annotation.getAnnotationType()),
//                        state.getTypeFromString(ENDPOINT_ANNOTATION),
//                        state));
//
//        if (!isEndpoint) {
//            return Description.NO_MATCH;
//        }
//
//        EndpointAssociatedExceptionScanner scanner = new EndpointAssociatedExceptionScanner();
//        // Scan the method body, passing the current state.
//        if (tree.getBody() != null) {
//            scanner.scan(new TreePath(state.getPath(), tree.getBody()), state);
//        }
//
//
//        Set<String> thrownExceptions = scanner.getThrownExceptions();
//        if (thrownExceptions.isEmpty()) {
//            return buildDescription(tree)
//                    .setMessage("Methods annotated with @Endpoint must throw an exception that extends "
//                            + ENDPOINT_SERVICE_EXCEPTION + ".")
//                    .build();
//        }
//
//        return Description.NO_MATCH;
//    }
//
//    // Helper method to check if an exception is caught and not re-thrown
//    private static boolean isExceptionCaught(TreePath path, Type thrownType, VisitorState state) {
//        TreePath currentPath = path;
//        while (currentPath != null) {
//            TryTree tryTree = ASTHelpers.findEnclosingNode(currentPath, TryTree.class);
//            if (tryTree == null) {
//                // No enclosing try-catch block from this point upwards in this method
//                return false;
//            }
//
//            // Check if this tryTree is still within the same method body or lambda body
//            // This check is important if isExceptionCaught is called from a deep AST path.
//            MethodTree enclosingMethod = ASTHelpers.findEnclosingNode(tryTree.getPath(), MethodTree.class);
//            LambdaExpressionTree enclosingLambda = ASTHelpers.findEnclosingNode(tryTree.getPath(),
// LambdaExpressionTree.class);
//
//            MethodTree originalMethod = ASTHelpers.findEnclosingNode(path, MethodTree.class);
//            LambdaExpressionTree originalLambda = ASTHelpers.findEnclosingNode(path, LambdaExpressionTree.class);
//
//            if ((enclosingMethod == null || !enclosingMethod.equals(originalMethod)) &&
//                    (enclosingLambda == null || !enclosingLambda.equals(originalLambda))) {
//                // The found TryTree is outside the current method/lambda scope of the throw.
//                return false;
//            }
//
//
//            for (CatchTree catchTree : tryTree.getCatches()) {
//                Type caughtType = ASTHelpers.getType(catchTree.getParameter().getType());
//                if (caughtType != null && state.getTypes().isSubtype(thrownType, caughtType)) {
//                    // The specific thrownType is potentially caught.
//                    // Check if the catch block re-throws it or a compatible EndpointServiceException.
//                    CatchBlockThrowsScanner catchScanner =
//                            new CatchBlockThrowsScanner(state, thrownType,
// state.getTypeFromString(ENDPOINT_SERVICE_EXCEPTION));
//                    catchScanner.scan(new TreePath(new TreePath(currentPath, tryTree), catchTree.getBlock()), state);
//                    if (!catchScanner.mightRethrow()) {
//                        return true; // Caught and not re-thrown by this catch block
//                    }
//                    // If it might be re-thrown by this catch, it's not truly handled at this level.
//                    // Continue to see if an outer try-catch handles the re-thrown exception.
//                }
//            }
//
//            // If not caught by this tryTree's catches, or caught and rethrown,
//            // move to the parent of the tryTree to check for outer try-catch blocks.
//            currentPath = tryTree.getPath().getParentPath();
//        }
//        return false; // Not caught by any enclosing try-catch within the method, or caught and re-thrown upwards
//    }
//
//
//    // Scans a catch block to see if it re-throws the caught exception or another EndpointServiceException
//    private static class CatchBlockThrowsScanner extends TreePathScanner<Void, VisitorState> {
//        private final VisitorState analysisState;
//        private final Type originallyCaughtType;
//        private final Type endpointServiceExceptionBaseType;
//        private boolean mightRethrow = false;
//
//        CatchBlockThrowsScanner(VisitorState analysisState, Type originallyCaughtType, Type
// endpointServiceExceptionBaseType) {
//            this.analysisState = analysisState;
//            this.originallyCaughtType = originallyCaughtType;
//            this.endpointServiceExceptionBaseType = endpointServiceExceptionBaseType;
//        }
//
//        public boolean mightRethrow() {
//            return mightRethrow;
//        }
//
//        @Override
//        public Void visitThrow(ThrowTree throwTree, VisitorState state) {
//            if (mightRethrow) return super.visitThrow(throwTree, state);
//
//            Type rethrownType = ASTHelpers.getType(throwTree.getExpression());
//            if (rethrownType != null) {
//                // Check if it re-throws the originallyCaughtType (or a subtype)
//                if (analysisState.getTypes().isSubtype(rethrownType, originallyCaughtType)) {
//                    mightRethrow = true;
//                }
//                // Check if it throws any EndpointServiceException (or a subtype)
//                else if (endpointServiceExceptionBaseType != null && !endpointServiceExceptionBaseType.isErroneous()
// &&
//                        analysisState.getTypes().isSubtype(rethrownType, endpointServiceExceptionBaseType)) {
//                    mightRethrow = true;
//                }
//            }
//            return super.visitThrow(throwTree, state);
//        }
//
//        @Override
//        public Void visitMethodInvocation(MethodInvocationTree invocationTree, VisitorState state) {
//            if (mightRethrow) return super.visitMethodInvocation(invocationTree, state);
//
//            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(invocationTree);
//            if (methodSymbol != null) {
//                // Check declared thrown types
//                for (Type declaredThrownType : methodSymbol.getThrownTypes()) {
//                    if (analysisState.getTypes().isSubtype(declaredThrownType, originallyCaughtType)) {
//                        mightRethrow = true;
//                        return super.visitMethodInvocation(invocationTree, state);
//                    }
//                    if (endpointServiceExceptionBaseType != null && !endpointServiceExceptionBaseType.isErroneous() &&
//                            analysisState.getTypes().isSubtype(declaredThrownType, endpointServiceExceptionBaseType))
// {
//                        mightRethrow = true;
//                        return super.visitMethodInvocation(invocationTree, state);
//                    }
//                }
//                // Detecting undeclared unchecked exceptions from method calls here is hard,
//                // similar to the main scanner. For simplicity, this catch block scanner
//                // primarily focuses on direct throws and declared throws within the catch.
//            }
//            return super.visitMethodInvocation(invocationTree, state);
//        }
//    }
//
//    private static final class EndpointAssociatedExceptionScanner extends TreePathScanner<Void, VisitorState> {
//        private final Set<String> thrownExceptions = new HashSet<>();
//
//        EndpointAssociatedExceptionScanner() {}
//
//        public Set<String> getThrownExceptions() {
//            return Collections.unmodifiableSet(thrownExceptions);
//        }
//
//        @Override
//        public Void visitThrow(ThrowTree throwTree, VisitorState state) {
//            Type thrownExpressionType = ASTHelpers.getType(throwTree.getExpression());
//            Type targetExceptionType = state.getTypeFromString(ENDPOINT_SERVICE_EXCEPTION);
//
//            if (targetExceptionType != null && !targetExceptionType.isErroneous() &&
//                    thrownExpressionType != null &&
//                    state.getTypes().isSubtype(thrownExpressionType, targetExceptionType)) {
//
//                if (!isExceptionCaught(getCurrentPath(), thrownExpressionType, state)) {
//                    if (thrownExpressionType.tsym != null) {
//                        thrownExceptions.add(thrownExpressionType.tsym.getQualifiedName().toString());
//                    } else {
//                        thrownExceptions.add(thrownExpressionType.toString());
//                    }
//                }
//            }
//            return super.visitThrow(throwTree, state);
//        }
//
//        @Override
//        public Void visitMethodInvocation(MethodInvocationTree invocationTree, VisitorState state) {
//            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(invocationTree);
//            Type targetExceptionType = state.getTypeFromString(ENDPOINT_SERVICE_EXCEPTION);
//
//            if (methodSymbol != null && targetExceptionType != null && !targetExceptionType.isErroneous()) {
//                // Check declared (checked) exceptions from the method signature
//                for (Type declaredThrownType : methodSymbol.getThrownTypes()) {
//                    if (state.getTypes().isSubtype(declaredThrownType, targetExceptionType)) {
//                        if (!isExceptionCaught(getCurrentPath(), declaredThrownType, state)) {
//                            if (declaredThrownType.tsym != null) {
//                                thrownExceptions.add(declaredThrownType.tsym.getQualifiedName().toString());
//                            } else {
//                                thrownExceptions.add(declaredThrownType.toString());
//                            }
//                            // Found one, can break if we only care that *an* ESE is thrown.
//                            // Or continue if we want to collect all distinct types.
//                        }
//                    }
//                }
//
//                // For UNDECLARED UNCHECKED EndpointServiceExceptions from this method call:
//                // As you pointed out, getThrownTypes() won't list these.
//                // Detecting them requires analyzing the body of 'methodSymbol', which is complex.
//                // This current version of the scanner will NOT detect such undeclared unchecked exceptions
//                // originating from method calls.
//            }
//            return super.visitMethodInvocation(invocationTree, state);
//        }
//    }
// }
