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
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Authentication token information should never be logged as it poses a security risk. Prevents "
                + "AuthHeader and BearerToken information from being passed to common logging calls.")
public final class PreventTokenLogging extends BaselineBugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final ImmutableList<String> TOKEN_TYPES =
            ImmutableList.of("com.palantir.tokens.auth.AuthHeader", "com.palantir.tokens.auth.BearerToken");

    private static final Matcher<ExpressionTree> METHOD_MATCHER = Matchers.anyOf(
            MethodMatchers.instanceMethod().onDescendantOf("org.slf4j.Logger"),
            MethodMatchers.staticMethod()
                    .onClassAny("com.palantir.logsafe.SafeArg", "com.palantir.logsafe.UnsafeArg")
                    .named("of"));

    private static final Matcher<ExpressionTree> AUTH_MATCHER = Matchers.anyOf(TOKEN_TYPES.stream()
            .flatMap(PreventTokenLogging::getMatchersForTokenType)
            .collect(ImmutableList.toImmutableList()));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (METHOD_MATCHER.matches(tree, state)) {
            for (ExpressionTree arg : tree.getArguments()) {
                if (AUTH_MATCHER.matches(arg, state)) {
                    return buildDescription(arg)
                            .setMessage("Authentication information is not allowed to be logged.")
                            .build();
                }
            }
        }
        return Description.NO_MATCH;
    }

    private static Stream<Matcher<ExpressionTree>> getMatchersForTokenType(String tokenType) {
        return Stream.of(
                // Fail if the arg is a subtype of a token.
                MoreMatchers.isSubtypeOf(tokenType),
                // Fail if the arg is calling a method on a token (e.g. token.toString()).
                // Note that this can't handle indirections.
                MethodMatchers.instanceMethod().onDescendantOf(tokenType));
    }
}
