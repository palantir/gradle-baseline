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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Set;
import javax.lang.model.element.ElementKind;

/**
 * ideas:
 * - get the conjure IR?
 * - in a Gradle task, collect all the files that could be Conjure-generated interfaces, and write their names into a
 *   file on disk.
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Ensure that methods annotated with @Endpoint are associated with an exception that extends"
                + " EndpointServiceException.")
public final class UnassociatedEndpointErrorV2 extends BugChecker implements BugChecker.MethodTreeMatcher {
    private static final String GENERATED_ANNOTATION = "javax.annotation.processing.Generated";
    private static final String UNDERTOW_SERVICE_INTERFACE_GENERATOR =
            "com.palantir.conjure.java.services.UndertowServiceInterfaceGenerator";

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (!isConjureEndpoint(tree, state)) {
            return Description.NO_MATCH;
        }

        // Use a TreeScanner to traverse the method body and analyze individual expressions
        ExceptionAnalysisScanner scanner = new ExceptionAnalysisScanner(state, tree);
        // Get a subset of the compilationUnit that only contains the method body.
        TreePath treePath = TreePath.getPath(state.getPath().getCompilationUnit(), tree);
        scanner.scan(treePath, state);

        Set<ClassSymbol> thrownExceptions = scanner.getThrownExceptions();
        if (!thrownExceptions.isEmpty()) {
            return buildDescription(tree)
                    .setMessage(String.format(
                            "Endpoint method can throw unassociated EndpointServiceException subtypes: %s", "test"))
                    .build();
        }

        return Description.NO_MATCH;
    }

    /**
     * Scanner that traverses the method body and uses dataflow analysis on individual expressions
     * to detect if EndpointServiceException subtypes can be thrown.
     */
    private static class ExceptionAnalysisScanner extends TreePathScanner<Set<ClassSymbol>, VisitorState> {
        private final VisitorState originalState;
        private final MethodTree targetMethod;
        private Set<ClassSymbol> thrownExceptions = new java.util.HashSet<>();

        ExceptionAnalysisScanner(VisitorState state, MethodTree targetMethod) {
            this.originalState = state;
            this.targetMethod = targetMethod;
        }

        Set<ClassSymbol> getThrownExceptions() {
            return thrownExceptions;
        }

        @Override
        public Set<ClassSymbol> reduce(Set<ClassSymbol> r1, Set<ClassSymbol> r2) {
            if (r1 == null) {
                return r2;
            }
            if (r2 == null) {
                return r1;
            }
            r1.addAll(r2);
            return r1;
        }

        @Override
        public Set<ClassSymbol> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            // Analyze this specific method invocation expression using dataflow analysis
            analyzeTree(node, state);
            return super.visitMethodInvocation(node, state);
        }

        @Override
        public Set<ClassSymbol> visitThrow(ThrowTree node, VisitorState state) {
            super.visitThrow(node, state);
            // Analyze the entire throw statement using dataflow analysis
            analyzeTree(node, state);
            return thrownExceptions;
        }

        //        @Override
        //        public Set<ClassSymbol> visitBlock(BlockTree node, VisitorState state) {
        //            super.visitBlock(node, state);
        //            // Analyze the entire block using dataflow analysis
        //            analyzeTree(node, state);
        //            return thrownExceptions;
        //        }

        @Override
        public Set<ClassSymbol> visitTry(TryTree node, VisitorState state) {
            super.visitTry(node, state);
            Set<ClassSymbol> thrownExceptionsFromTryBlock = super.visitBlock(node.getBlock(), state);
            thrownExceptions.addAll(thrownExceptionsFromTryBlock);
            for (CatchTree catchTree : node.getCatches()) {
                // Inspect the exceptions thrown in the try block, and remove any exceptions from the set that are
                // subtypes of caught exceptions
                Type caughtExceptionType = ASTHelpers.getType(catchTree.getParameter());
                if (caughtExceptionType == null) {
                    // This should not be possible, but could change in future java versions.
                    // avoid failing noisily in this case.
                    continue;
                }
                thrownExceptions.removeIf(type -> type.isSubClass(caughtExceptionType.tsym, state.getTypes()));
            }

            return thrownExceptions;
        }

        private void analyzeTree(Tree tree, VisitorState state) {
            try {
                // Use the current path from the TreePathScanner - this gives us the full path
                // from the compilation unit down to the current tree node
                TreePath currentPath = getCurrentPath();
                if (currentPath != null && currentPath.getLeaf() == tree) {
                    VisitorState treeState = state.withPath(currentPath);
                    Set<ClassSymbol> exceptions = ExceptionAnalysis.getThrownExceptionNames(treeState);
                    thrownExceptions.addAll(exceptions);
                }
            } catch (Exception e) {
                // no-op
            }
        }
    }

    /**
     * Check if this method is a Conjure endpoint that should be analyzed. Conjure endpoint methods are overrides of
     * methods defined in a Conjure-generated Undertow service interface.
     */
    private static boolean isConjureEndpoint(MethodTree tree, VisitorState state) {
        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        if (methodSymbol == null || methodSymbol.getKind() != ElementKind.METHOD) {
            return false;
        }

        Symbol.ClassSymbol enclosingClass = methodSymbol.enclClass();
        if (enclosingClass == null) {
            return false;
        }

        if (!isOverrideMethod(tree, state)) {
            return false;
        }

        for (Type interfaceSymbol : enclosingClass.getInterfaces()) {
            if (!(interfaceSymbol.tsym instanceof Symbol.ClassSymbol)) {
                continue;
            }
            // TODO(pm): Ideally we are able to check if the interface is generated by the
            //  UndertowServiceInterfaceGenerator, but the Generated annotation has a source retention policy, so it's
            //  not available. Let's think of ways to short-circuit faster here. Maybe an authheader check? How common
            //  are no-auth endpoints?
            return interfaceContainsMethodWithName(
                    interfaceSymbol, tree.getName().toString());
        }
        return false;
    }

    private static boolean isOverrideMethod(MethodTree tree, VisitorState state) {
        return ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(tree), Override.class.getName(), state);
    }

    private static boolean interfaceContainsMethodWithName(Type interfaceType, String methodName) {
        if (!(interfaceType.tsym instanceof Symbol.ClassSymbol interfaceSymbol)) {
            return false;
        }
        for (Symbol method : ASTHelpers.getEnclosedElements(interfaceSymbol)) {
            if (method.getKind() == ElementKind.METHOD
                    && method.getSimpleName().toString().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
