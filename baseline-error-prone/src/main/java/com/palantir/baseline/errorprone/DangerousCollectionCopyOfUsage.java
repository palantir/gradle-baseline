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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Map;
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousCollectionCopyOfUsage",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Disallow Java 10+ java.util.{Map,Set}.copyOf methods.")
public final class DangerousCollectionCopyOfUsage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final String ERROR_MESSAGE = "Should not use java.util.{Map,Set}.copyOf methods, and should instead "
            + "prefer Guava Immutable{Map,Set}.copyOf. Java's internal immutable collections are not hash based, they "
            + "are linear lookup based. This means that instead of O(1) lookup time, they have O(n) lookup time. This "
            + "risks hard-to-discover performance issues. Guava's immutable collections avoid this.";
    private static final Matcher<ExpressionTree> COPY_OF = MethodMatchers.staticMethod()
            .onClassAny(Map.class.getName(), Set.class.getName())
            .named("copyOf");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (COPY_OF.matches(tree, state)) {
            return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
        }
        return Description.NO_MATCH;
    }
}
