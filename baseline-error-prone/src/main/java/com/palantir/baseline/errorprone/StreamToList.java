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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "`Stream.toList()` is more efficient than `Stream.collect(Collectors.toUnmodifiableList())`")
public final class StreamToList extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAM_COLLECT = MethodMatchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .namedAnyOf("collect")
            .withParameters(Collector.class.getName());

    private static final Matcher<ExpressionTree> COLLECTORS_TO_UNMODIFIABLE_LIST = MethodMatchers.staticMethod()
            .onDescendantOf(Collectors.class.getName())
            .namedAnyOf("toUnmodifiableList")
            .withNoParameters();

    private static final Matcher<ExpressionTree> STREAM_COLLECT_TO_UNMODIFIABLE_LIST = Matchers.methodInvocation(
            STREAM_COLLECT,
            // Any of the three MatchTypes are reasonable in this case, given a single arg
            MatchType.LAST,
            COLLECTORS_TO_UNMODIFIABLE_LIST);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STREAM_COLLECT_TO_UNMODIFIABLE_LIST.matches(tree, state)) {
            ExpressionTree receiver = ASTHelpers.getReceiver(tree.getMethodSelect());
            if (receiver != null) {
                return buildDescription(tree)
                        .addFix(SuggestedFix.builder()
                                .replace(state.getEndPosition(receiver), state.getEndPosition(tree), ".toList()")
                                .build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }
}
