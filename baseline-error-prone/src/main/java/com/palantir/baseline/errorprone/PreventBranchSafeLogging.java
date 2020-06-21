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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreventBranchSafeLogging",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Branches are not safe loggable. If necessary they can be logged as unsafe.")
public final class PreventBranchSafeLogging extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> matcher = MethodMatchers.staticMethod()
            .onClassAny("com.palantir.logsafe.SafeArg")
            .named("of");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (matcher.matches(tree, state)) {
            if (tree.getArguments().isEmpty()) {
                return Description.NO_MATCH;
            }

            ExpressionTree arg = tree.getArguments().get(0);

            String parameterName = ASTHelpers.constValue(arg, String.class);

            if (parameterName.startsWith("branch")) {
                return buildDescription(arg)
                        .setMessage("Avoid logging the branch entirely, or log it as unsafe")
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
