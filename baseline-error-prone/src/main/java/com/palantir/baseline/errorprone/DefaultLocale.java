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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "Implicit use of the platform default locale, which can result in differing behaviour between JVM"
                + " executions.",
        severity = SeverityLevel.SUGGESTION)
public final class DefaultLocale extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> MATCHER = MethodMatchers.instanceMethod()
            .onExactClass("java.lang.String")
            .namedAnyOf("toLowerCase", "toUpperCase")
            .withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            return buildDescription(tree)
                    .addFix(fix.replace(
                                    state.getEndPosition(tree.getMethodSelect()),
                                    state.getEndPosition(tree),
                                    String.format(
                                            "(%s.ROOT)", SuggestedFixes.qualifyType(state, fix, "java.util.Locale")))
                            .build())
                    .build();
        }

        return Description.NO_MATCH;
    }
}
