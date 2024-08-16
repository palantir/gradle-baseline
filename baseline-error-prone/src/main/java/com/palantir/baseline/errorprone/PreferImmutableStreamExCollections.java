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
import java.util.Collections;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Prefer immutable/unmodifable collections wherever possible because they are inherently threadsafe "
                + "and easier to reason about when passed between different functions."
                + " If you really want a mutable output then explicitly suppress this check.")
public final class PreferImmutableStreamExCollections extends BaselineBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAMEX_TO_MAP = MethodMatchers.instanceMethod()
            .onExactClass("one.util.streamex.EntryStream")
            .named("toMap")
            .withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> STREAMEX_TO_SET = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .named("toSet")
            .withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> STREAMEX_TO_LIST = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .named("toList")
            .withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> STREAMEX_COLLECT = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .named("collect");

    private static final Matcher<ExpressionTree> COLLECT_TO_SET = MethodMatchers.staticMethod()
            .onClass("java.util.stream.Collectors")
            .named("toSet")
            .withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> COLLECT_TO_LIST = MethodMatchers.staticMethod()
            .onClass("java.util.stream.Collectors")
            .named("toList")
            .withParameters(Collections.emptyList());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STREAMEX_TO_MAP.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFixes.renameMethodInvocation(tree, "toImmutableMap", state))
                    .build();
        }

        if (STREAMEX_TO_SET.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFixes.renameMethodInvocation(tree, "toImmutableSet", state))
                    .build();
        }

        if (STREAMEX_TO_LIST.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFixes.renameMethodInvocation(tree, "toImmutableList", state))
                    .build();
        }

        if (STREAMEX_COLLECT.matches(tree, state) && tree.getArguments().size() == 1) {
            ExpressionTree argument = tree.getArguments().get(0);

            if (COLLECT_TO_SET.matches(argument, state)) {
                return buildDescription(tree)
                        .addFix(SuggestedFix.builder()
                                .delete(argument)
                                .merge(SuggestedFixes.renameMethodInvocation(tree, "toImmutableSet", state))
                                .build())
                        .build();
            }

            if (COLLECT_TO_LIST.matches(argument, state)) {
                return buildDescription(tree)
                        .addFix(SuggestedFix.builder()
                                .delete(argument)
                                .merge(SuggestedFixes.renameMethodInvocation(tree, "toImmutableList", state))
                                .build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
