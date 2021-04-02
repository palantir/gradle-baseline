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
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "OptionalOrElseGetValue",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "If lambda passed to Optional#orElseGet returns a simple expression, use Optional#orElse instead")
public final class OptionalOrElseGetValue extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;
    private static final Matcher<ExpressionTree> OR_ELSE_GET_METHOD =
            MethodMatchers.instanceMethod().onExactClass("java.util.Optional").named("orElseGet");
    private static final Matcher<ExpressionTree> COMPILE_TIME_CONSTANT = new CompileTimeConstantExpressionMatcher();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!OR_ELSE_GET_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        ExpressionTree orElseGetArg = tree.getArguments().get(0);

        if (orElseGetArg.getKind() != Tree.Kind.LAMBDA_EXPRESSION) {
            return Description.NO_MATCH;
        }

        LambdaExpressionTree lambdaExpressionTree = (LambdaExpressionTree) orElseGetArg;
        LambdaExpressionTree.BodyKind bodyKind = lambdaExpressionTree.getBodyKind();

        if (bodyKind != LambdaExpressionTree.BodyKind.EXPRESSION) {
            return Description.NO_MATCH;
        }

        ExpressionTree expressionBody = (ExpressionTree) lambdaExpressionTree.getBody();
        if (!COMPILE_TIME_CONSTANT.matches(expressionBody, state)
                && !isTrivialExpression(expressionBody)
                && !isTrivialSelect(expressionBody)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Prefer Optional#orElse instead of Optional#orElseGet for compile time constants")
                .addFix(SuggestedFix.builder()
                        .merge(SuggestedFixes.renameMethodInvocation(tree, "orElse", state))
                        .replace(orElseGetArg, state.getSourceForNode(expressionBody))
                        .build())
                .build();
    }

    private static boolean isTrivialExpression(ExpressionTree tree) {
        return tree instanceof LiteralTree || tree instanceof IdentifierTree;
    }

    private static boolean isTrivialSelect(ExpressionTree tree) {
        return tree instanceof MemberSelectTree && isTrivialExpression(((MemberSelectTree) tree).getExpression());
    }
}
