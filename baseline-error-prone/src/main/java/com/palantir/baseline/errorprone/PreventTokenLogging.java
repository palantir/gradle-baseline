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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreventTokenLogging",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Authentication token information should never be logged as it poses a security risk. Prevents "
                + "AuthHeader and BearerToken information from being passed to common logging calls.")
public final class PreventTokenLogging extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> METHOD_MATCHER =
            Matchers.anyOf(
                    MethodMatchers.instanceMethod()
                            .onExactClass("org.slf4j.Logger")
                            .withNameMatching(Pattern.compile("trace|debug|info|warn|error")),
                    MethodMatchers.staticMethod()
                            .onClass("com.palantir.logsafe.SafeArg")
                            .named("of"),
                    MethodMatchers.staticMethod()
                            .onClass("com.palantir.logsafe.UnsafeArg")
                            .named("of"));

    private static final Matcher<Tree> AUTH_MATCHER =
            Matchers.anyOf(
                    Matchers.isSubtypeOf("com.palantir.tokens.auth.AuthHeader"),
                    Matchers.isSubtypeOf("com.palantir.tokens.auth.BearerToken"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (METHOD_MATCHER.matches(tree, state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() < 2) {
                return Description.NO_MATCH;
            }

            for (Tree arg : args.subList(1, args.size())) {
                if (AUTH_MATCHER.matches(arg, state)) {
                    return buildDescription(arg)
                            .setMessage("Authentication information is not allowed to be logged.")
                            .build();
                }
            }
        }
        return Description.NO_MATCH;
    }
}
