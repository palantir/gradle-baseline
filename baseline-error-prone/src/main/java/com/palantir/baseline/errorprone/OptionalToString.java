/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Optional.toString() does not stringifies the value contained by the Optional"
                + " object. Did you mean Optional.get().toString() instead?")
public final class OptionalToString extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> OPTIONAL_TO_STRING_METHOD = MethodMatchers.instanceMethod()
            .onExactClassAny(Optional.class.getName(), com.google.common.base.Optional.class.getName())
            .named("toString")
            .withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInvocationTree, VisitorState visitorState) {
        if (OPTIONAL_TO_STRING_METHOD.matches(methodInvocationTree, visitorState)) {
            return describeMatch(methodInvocationTree);
        }

        return Description.NO_MATCH;
    }
}
