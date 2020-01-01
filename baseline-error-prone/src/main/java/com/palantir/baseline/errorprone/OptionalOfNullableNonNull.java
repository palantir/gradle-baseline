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
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "OptionalOfNullableNonNull",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Optional.of should be used for non-null values. This produces code that is more obvious, "
                + "making it easier for developers to discover unnecessary optionals.")
public final class OptionalOfNullableNonNull extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> matcher = Matchers.methodInvocation(
            MethodMatchers.staticMethod()
                    .onClass(Optional.class.getName())
                    .named("ofNullable")
                    .withParameters(Object.class.getName()),
            ChildMultiMatcher.MatchType.LAST,
            Matchers.isNonNull());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!matcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .addFix(MoreSuggestedFixes.renameInvocationRetainingTypeArguments(tree, "of", state))
                .build();
    }
}
