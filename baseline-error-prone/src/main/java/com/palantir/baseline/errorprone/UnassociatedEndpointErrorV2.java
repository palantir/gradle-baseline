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
import com.sun.tools.javac.code.Type;
import java.util.Set;
import javax.lang.model.element.ElementKind;

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
        scanner.scan(state.getPath().getCompilationUnit(), state);

        Set<String> thrownExceptions = scanner.getThrownExceptions();
        if (!thrownExceptions.isEmpty()) {
            return buildDescription(tree)
                    .setMessage(String.format(
                            "Endpoint method can throw unassociated EndpointServiceException subtypes: %s",
                            String.join(", ", thrownExceptions)))
                    .build();
        }

        return Description.NO_MATCH;
    }

    /**
     * Scanner that traverses the method body and uses dataflow analysis on individual expressions
     * to detect if EndpointServiceException subtypes can be thrown.
     */
    private static class ExceptionAnalysisScanner extends TreePathScanner<Set<String>, VisitorState> {
        private final VisitorState originalState;
        private final MethodTree targetMethod;
        private Set<String> thrownExceptions = new java.util.HashSet<>();

        ExceptionAnalysisScanner(VisitorState state, MethodTree targetMethod) {
            this.originalState = state;
            this.targetMethod = targetMethod;
        }

        Set<String> getThrownExceptions() {
            return thrownExceptions;
        }

        @Override
        public Set<String> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            // Analyze this specific method invocation expression using dataflow analysis
            analyzeTree(node, state);
            return super.visitMethodInvocation(node, state);
        }

        @Override
        public Set<String> visitThrow(ThrowTree node, VisitorState state) {
            // Analyze the entire throw statement using dataflow analysis
            analyzeTree(node, state);
            return super.visitThrow(node, state);
        }

        @Override
        public Set<String> visitTry(TryTree node, VisitorState state) {
            Set<String> exceptionsThrown = super.visitBlock(node.getBlock(), state);
            if (exceptionsThrown != null && !exceptionsThrown.isEmpty()) {
                for (CatchTree catchTree : node.getCatches()) {
                    // Get the exception types caught

                }
            }
            return super.visitTry(node, state);
        }

        private void analyzeTree(Tree tree, VisitorState state) {
            try {
                // Use the current path from the TreePathScanner - this gives us the full path
                // from the compilation unit down to the current tree node
                TreePath currentPath = getCurrentPath();
                if (currentPath != null && currentPath.getLeaf() == tree) {
                    VisitorState treeState = state.withPath(currentPath);
                    Set<String> exceptions = ExceptionAnalysis.getThrownExceptionNames(treeState);
                    thrownExceptions.addAll(exceptions);
                }
            } catch (Exception e) {
                // If dataflow analysis fails for this tree, continue with other trees
                // This can happen for complex expressions or edge cases
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
