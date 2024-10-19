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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Disallow usage of Guava Map's .transformValues().")
public final class DangerousGuavaTransformValuesUsage extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;
    private static final String ERROR_MESSAGE = "The transformValues API of Guava Maps creates a lazily evaluated "
            + "view of the source Map. Repeated access of the same key leads to repeated evaluations of the "
            + "transformer function. This is often unintended and can cause severe performance degradation."
            + "Where this is actually intended, suppress this warning.";

    private static final Matcher<ExpressionTree> TRANSFORM_VALUES_CALL = MethodMatchers.instanceMethod()
            .onExactClass("com.google.common.collect.Maps")
            .named("transformValues");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!TRANSFORM_VALUES_CALL.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // Fail on any 'transformValues(...)' usage
        return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
    }
}
