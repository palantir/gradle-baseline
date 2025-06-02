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
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
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
    private static final String ENDPOINT_SERVICE_EXCEPTION = "EndpointServiceException";

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (!isConjureEndpoint(tree, state)) {
            return Description.NO_MATCH;
        }
        // Scan the method body for EndpointServiceExceptions
        EndpointAssociatedExceptionScanner scanner = new EndpointAssociatedExceptionScanner();
        scanner.scan(state.getPath().getCompilationUnit(), null);

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
    private static final class EndpointAssociatedExceptionScanner extends TreePathScanner<Void, Void> {
        private final Set<String> thrownExceptions = new HashSet<>();

        private EndpointAssociatedExceptionScanner() {}

        public Set<String> getThrownExceptions() {
            return Collections.unmodifiableSet(thrownExceptions);
        }

        @Override
        @SuppressWarnings("BanSystemOut")
        public Void visitThrow(ThrowTree node, Void unused) {
            // Check if the thrown exception is an unchecked subtype CheckedServiceException
            Type thrownType = ASTHelpers.getType(node.getExpression());
            if (thrownType == null) {
                return super.visitThrow(node, unused);
            }
            if (!(thrownType.tsym instanceof ClassSymbol thrownClassSymbol)) {
                return super.visitThrow(node, unused);
            }

            Type superclass = thrownClassSymbol.getSuperclass();
            if (superclass != null
                    && superclass.tsym.getQualifiedName().toString().contains(ENDPOINT_SERVICE_EXCEPTION)) {
                System.out.println("Found exception thrown: " + thrownClassSymbol.getQualifiedName());
                thrownExceptions.add(thrownClassSymbol.getQualifiedName().toString());
            }

            return super.visitThrow(node, unused);
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
                System.out.println("Found conjure endpoint method: " + method.getSimpleName());
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
