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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferBuiltInConcurrentKeySet",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary =
                "Prefer Java's built-in Concurrent Set implementation over Guava's ConcurrentHashSet, as it does "
                        + "the same thing with less indirection and doesn't rely on Guava")
public final class PreferBuiltInConcurrentKeySet extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newConcurrentHashSet")
            .withParameters();

    private static final String ERROR_MESSAGE =
            "Prefer Java's built-in Concurrent Set implementation over Guava's ConcurrentHashSet, as it does "
                    + "same thing with less indirection and doesn't rely on Guava";

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String qualifiedType = MoreSuggestedFixes.qualifyType(state, fix, "java.util.concurrent.ConcurrentHashMap");
            return buildDescription(tree)
                    .setMessage(ERROR_MESSAGE)
                    .addFix(fix.replace(tree.getMethodSelect(), qualifiedType + ".newKeySet")
                            .build())
                    .build();
        }

        return Description.NO_MATCH;
    }
}
