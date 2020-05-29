/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LambdaMethodReference",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Lambda should be a method reference")
public final class LambdaMethodReference extends BugChecker implements BugChecker.LambdaExpressionTreeMatcher {

    private static final String MESSAGE = "Lambda should be a method reference";

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree tree, VisitorState state) {
        LambdaExpressionTree.BodyKind bodyKind = tree.getBodyKind();
        Tree body = tree.getBody();
        // n.b. These checks are meant to avoid any and all cleverness. The goal is to be confident
        // that we can fix the most basic problems correctly, not to take risks and produce code
        // that may not compile.
        switch (bodyKind) {
            case EXPRESSION:
                if (!(body instanceof MethodInvocationTree)) {
                    return Description.NO_MATCH;
                }
                return checkMethodInvocation((MethodInvocationTree) body, tree, state);
            case STATEMENT:
                if (!(body instanceof BlockTree)) {
                    return Description.NO_MATCH;
                }
                BlockTree block = (BlockTree) body;
                if (block.getStatements().size() != 1) {
                    return Description.NO_MATCH;
                }
                StatementTree statement = block.getStatements().get(0);
                if (!(statement instanceof ReturnTree)) {
                    return Description.NO_MATCH;
                }
                ReturnTree returnStatement = (ReturnTree) statement;
                ExpressionTree returnExpression = returnStatement.getExpression();
                if (!(returnExpression instanceof MethodInvocationTree)) {
                    return Description.NO_MATCH;
                }
                return checkMethodInvocation((MethodInvocationTree) returnExpression, tree, state);
        }
        throw new IllegalStateException("Unexpected BodyKind: " + bodyKind);
    }

    private Description checkMethodInvocation(
            MethodInvocationTree methodInvocation, LambdaExpressionTree root, VisitorState state) {
        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodInvocation);
        if (methodSymbol == null || !methodInvocation.getTypeArguments().isEmpty()) {
            return Description.NO_MATCH;
        }

        if (methodInvocation.getArguments().isEmpty()
                && (root.getParameters().size() == 1 || root.getParameters().isEmpty())) {
            return convertSuppliersAndVariableInstanceMethods(methodSymbol, methodInvocation, root, state);
        }

        if (methodInvocation.getArguments().size() == root.getParameters().size()) {
            return convertMethodInvocations(methodSymbol, methodInvocation, root, state);
        }

        return Description.NO_MATCH;
    }

    private Description convertSuppliersAndVariableInstanceMethods(
            Symbol.MethodSymbol methodSymbol,
            MethodInvocationTree methodInvocation,
            LambdaExpressionTree root,
            VisitorState state) {
        if (!root.getParameters().isEmpty()) {
            Symbol paramSymbol = ASTHelpers.getSymbol(Iterables.getOnlyElement(root.getParameters()));
            Symbol receiverSymbol = ASTHelpers.getSymbol(ASTHelpers.getReceiver(methodInvocation));
            if (!paramSymbol.equals(receiverSymbol)) {
                return Description.NO_MATCH;
            }
        }

        ExpressionTree receiver = ASTHelpers.getReceiver(methodInvocation);
        boolean isLocal = isLocal(methodInvocation);
        if (!isLocal && !(receiver instanceof IdentifierTree)) {
            return Description.NO_MATCH;
        }

        return buildDescription(root)
                .setMessage(MESSAGE)
                .addFix(buildFix(methodSymbol, methodInvocation, root, state, isLocal))
                .build();
    }

    private Description convertMethodInvocations(
            Symbol.MethodSymbol methodSymbol,
            MethodInvocationTree methodInvocation,
            LambdaExpressionTree root,
            VisitorState state) {
        List<Symbol> methodParams = getSymbols(methodInvocation.getArguments());
        List<Symbol> lambdaParam = getSymbols(root.getParameters());

        // We are guaranteed that all of root params are symbols so equality should handle cases where methodInvocation
        // arguments are not symbols or are out of order
        if (!methodParams.equals(lambdaParam)) {
            return Description.NO_MATCH;
        }

        return buildDescription(root)
                .setMessage(MESSAGE)
                .addFix(buildFix(methodSymbol, methodInvocation, root, state, isLocal(methodInvocation)))
                .build();
    }

    private static List<Symbol> getSymbols(List<? extends Tree> params) {
        return params.stream()
                .map(ASTHelpers::getSymbol)
                .filter(Objects::nonNull)
                .collect(ImmutableList.toImmutableList());
    }

    private static Optional<SuggestedFix> buildFix(
            Symbol.MethodSymbol symbol,
            MethodInvocationTree invocation,
            LambdaExpressionTree root,
            VisitorState state,
            boolean isLocal) {
        SuggestedFix.Builder builder = SuggestedFix.builder();
        return toMethodReference(qualifyTarget(symbol, invocation, builder, state, isLocal))
                .map(qualified -> builder.replace(root, qualified).build());
    }

    private static String qualifyTarget(
            Symbol.MethodSymbol symbol,
            MethodInvocationTree invocation,
            SuggestedFix.Builder builder,
            VisitorState state,
            boolean isLocal) {
        if (!symbol.isStatic() && isLocal) {
            return "this." + symbol.name.toString();
        }

        Type receiverType = ASTHelpers.getReceiverType(invocation);
        if (receiverType == null || receiverType.getLowerBound() != null || receiverType.getUpperBound() != null) {
            return SuggestedFixes.qualifyType(state, builder, symbol);
        }
        return SuggestedFixes.qualifyType(state, builder, state.getTypes().erasure(receiverType))
                + '.'
                + symbol.name.toString();
    }

    private static Optional<String> toMethodReference(String qualifiedMethodName) {
        int index = qualifiedMethodName.lastIndexOf('.');
        if (index > 0) {
            return Optional.of(
                    qualifiedMethodName.substring(0, index) + "::" + qualifiedMethodName.substring(index + 1));
        }
        return Optional.empty();
    }

    private static boolean isLocal(MethodInvocationTree methodInvocationTree) {
        ExpressionTree receiver = ASTHelpers.getReceiver(methodInvocationTree);
        return receiver == null
                || (receiver instanceof IdentifierTree
                        && "this".equals(((IdentifierTree) receiver).getName().toString()));
    }
}
