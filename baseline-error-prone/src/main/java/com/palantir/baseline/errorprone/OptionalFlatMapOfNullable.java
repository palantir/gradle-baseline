/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@AutoService(BugChecker.class)
@BugPattern(
        name = "OptionalFlatMapOfNullable",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Optional.map functions may return null to safely produce an empty result.")
public final class OptionalFlatMapOfNullable extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> flatMap = MethodMatchers.instanceMethod()
            .onDescendantOf(Optional.class.getName())
            .named("flatMap")
            .withParameters(Function.class.getName());

    private static final Matcher<ExpressionTree> ofNullable = MethodMatchers.staticMethod()
            .onClass(Optional.class.getName())
            .named("ofNullable")
            .withParameters(Object.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!flatMap.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ExpressionTree functionParameter =
                ASTHelpers.stripParentheses(tree.getArguments().get(0));
        if (functionParameter.getKind() != Tree.Kind.LAMBDA_EXPRESSION) {
            return Description.NO_MATCH;
        }
        LambdaExpressionTree lambdaExpressionTree = (LambdaExpressionTree) functionParameter;
        Optional<ExpressionTree> maybeExpression = finalExpression(lambdaExpressionTree);
        if (!maybeExpression.isPresent()) {
            return Description.NO_MATCH;
        }
        ExpressionTree expression = maybeExpression.get();
        if (!ofNullable.matches(expression, state)) {
            return Description.NO_MATCH;
        }
        MethodInvocationTree ofNullableInvocation = (MethodInvocationTree) expression;
        ExpressionTree ofNullableArg = ofNullableInvocation.getArguments().get(0);
        return buildDescription(tree)
                .addFix(SuggestedFix.builder()
                        .merge(SuggestedFixes.renameMethodInvocation(tree, "map", state))
                        .replace(ofNullableInvocation, state.getSourceForNode(ofNullableArg))
                        .build())
                .build();
    }

    private Optional<ExpressionTree> finalExpression(LambdaExpressionTree lambdaExpressionTree) {
        Tree body = lambdaExpressionTree.getBody();
        switch (lambdaExpressionTree.getBodyKind()) {
            case EXPRESSION:
                return Optional.of((ExpressionTree) body);
            case STATEMENT:
                if (body instanceof BlockTree) {
                    BlockTree block = (BlockTree) body;
                    List<? extends StatementTree> statements = block.getStatements();
                    if (!statements.isEmpty()) {
                        StatementTree finalStatement = statements.get(statements.size() - 1);
                        if (finalStatement instanceof ReturnTree) {
                            ReturnTree returnTree = (ReturnTree) finalStatement;
                            return Optional.ofNullable(returnTree.getExpression());
                        }
                    }
                }
                break;
        }
        // Don't break compilation when new language features are introduced.
        return Optional.empty();
    }
}
