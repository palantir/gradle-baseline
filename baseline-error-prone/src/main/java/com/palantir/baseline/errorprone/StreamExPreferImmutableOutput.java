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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Collections;

@AutoService(BugChecker.class)
@BugPattern(
        name = "StreamExPreferImmutableStreamOutputs",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "Prefer immutable/unmodifable collections wherever possible because they are innately threadsafe, "
                + "and easier to reason about when passed between different functions."
                + " If you really want a mutable output then explicitly suppress this check.")
public final class StreamExPreferImmutableOutput extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> TO_MAP = MethodMatchers.instanceMethod()
            .onExactClass("one.util.streamex.EntryStream")
            .named("toMap")
            .withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> TO_SET = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .named("toSet")
            .withParameters(Collections.emptyList());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (TO_MAP.matches(tree, state)) {
            String base = state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect()));
            return buildDescription(tree)
                    .setMessage("Prefer .toImmutableMap()")
                    .addFix(SuggestedFix.replace(tree.getMethodSelect(), base + ".toImmutableMap"))
                    .build();
        }

        if (TO_SET.matches(tree, state)) {
            String base = state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect()));
            return buildDescription(tree)
                    .setMessage("Prefer .toImmutableSet()")
                    .addFix(SuggestedFix.replace(tree.getMethodSelect(), base + ".toImmutableSet"))
                    .build();
        }

        return Description.NO_MATCH;
    }
}
