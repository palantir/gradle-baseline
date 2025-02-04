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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.List;
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "Prefer `when` over `doReturn` for stubbing",
        severity = BugPattern.SeverityLevel.ERROR,
        documentSuppression = false)
public final class PreferTypeSafeStubbing extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> IS_DO_RETURN = Matchers.instanceMethod()
            .onDescendantOf("org.mockito.stubbing.Stubber")
            .named("when");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInvocationTree, VisitorState visitorState) {
        ExpressionTree whenPart = ASTHelpers.getReceiver(methodInvocationTree);
        if (!IS_DO_RETURN.matches(whenPart, visitorState)) {
            return Description.NO_MATCH;
        }

        Set<VarSymbol> mocks = findMocks(visitorState);
        Tree mockArgumentTree = ((MethodInvocationTree) whenPart).getArguments().get(0);
        Symbol mockSymbol = ASTHelpers.getSymbol(mockArgumentTree);

        if (!mocks.contains(mockSymbol)) {
            return Description.NO_MATCH;
        }

        List<String> doReturnParts = Lists.reverse(ASTHelpers.streamReceivers(whenPart)
                .map(innerDoReturnPart -> ((MethodInvocationTree) innerDoReturnPart)
                        .getArguments()
                        .get(0))
                .map(visitorState::getSourceForNode)
                .map(".thenReturn(%s)"::formatted)
                .toList());
        String argMatchers = String.join(
                ", ",
                methodInvocationTree.getArguments().stream()
                        .map(visitorState::getSourceForNode)
                        .toList());
        String replacement = "when(%s.%s(%s))%s"
                .formatted(
                        mockSymbol,
                        ASTHelpers.getSymbol(methodInvocationTree.getMethodSelect()).name,
                        argMatchers,
                        String.join("", doReturnParts));

        return buildDescription(methodInvocationTree)
                .addFix(SuggestedFix.builder()
                        .replace(methodInvocationTree, replacement)
                        .addStaticImport("org.mockito.Mockito.when")
                        .build())
                .build();
    }

    private Set<VarSymbol> findMocks(VisitorState state) {
        ImmutableSet.Builder<VarSymbol> mocks = ImmutableSet.builder();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree tree, Void unused) {
                if (tree.getInitializer() != null && MOCK.matches(tree.getInitializer(), state)) {
                    mocks.add(ASTHelpers.getSymbol(tree));
                }
                return super.visitVariable(tree, null);
            }

            @Override
            public Void visitAssignment(AssignmentTree tree, Void unused) {
                if (MOCK.matches(tree.getExpression(), state)) {
                    Symbol symbol = ASTHelpers.getSymbol(tree.getVariable());
                    if (symbol instanceof VarSymbol varSymbol) {
                        mocks.add(varSymbol);
                    }
                }
                return super.visitAssignment(tree, null);
            }
        }.scan(state.getPath().getCompilationUnit(), null);
        return mocks.build();
    }

    private static final Matcher<ExpressionTree> MOCK = Matchers.anyOf(
            Matchers.staticMethod().onClass("org.mockito.Mockito").named("mock").withParameters("java.lang.Class"),
            Matchers.staticMethod()
                    .onClass("org.mockito.Mockito")
                    .named("mock")
                    .withParametersOfType(ImmutableList.of(Suppliers.arrayOf(Suppliers.OBJECT_TYPE))));
}
