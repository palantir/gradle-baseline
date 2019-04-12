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
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "GradleCacheableTaskAction",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Forbid gradle actions (doFirst, doLast) to be implemented by lambdas.")
public final class GradleCacheableTaskAction extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;
    private static final Matcher<Tree> IS_ACTION = Matchers.isSubtypeOf("org.gradle.api.Action");

    private static final Matcher<ExpressionTree> TASK_ACTION = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .withNameMatching(Pattern.compile("doFirst|doLast"));

    private static final Matcher<ExpressionTree> IS_LAMBDA_EXPRESSION =
            (expression, state) -> expression instanceof LambdaExpressionTree;

    private static final Matcher<MethodInvocationTree> TASK_ACTION_WITH_LAMBDA = Matchers.allOf(
            TASK_ACTION,
            Matchers.hasArguments(MatchType.LAST, Matchers.allOf(IS_ACTION, IS_LAMBDA_EXPRESSION)));

    @Override
    public Description matchMethodInvocation(
            MethodInvocationTree tree, VisitorState state) {
        if (!TASK_ACTION_WITH_LAMBDA.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ExpressionTree lastArgument = Iterables.getLast(tree.getArguments());
        return buildDescription(lastArgument)
                .setMessage("Gradle task actions are not cacheable when implemented by lambdas")
                .build();
    }
}
