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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LogsafeArgument",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Prevent certain arguments from being logged.")
public final class LogsafeArgument extends BugChecker implements MethodInvocationTreeMatcher {
    static final String UNSAFE_ARG_NAMES_FLAG = "LogsafeArgName:UnsafeArgNames";

    private static final Matcher<ExpressionTree> SAFE_ARG_OF =
            Matchers.staticMethod().onClass("com.palantir.logsafe.SafeArg").named("of");
    private static final Matcher<ExpressionTree> UNSAFE_ARG_OF =
            Matchers.staticMethod().onClass("com.palantir.logsafe.UnsafeArg").named("of");
    private static final Matcher<ExpressionTree> THROWABLE = MoreMatchers.isSubtypeOf("java.lang.Throwable");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!SAFE_ARG_OF.matches(tree, state) && !UNSAFE_ARG_OF.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (THROWABLE.matches(args.get(1), state)) {
            return buildDescription(tree)
                    .setMessage("Args with type Throwable are not allowed.")
                    .build();
        }

        return Description.NO_MATCH;
    }
}
