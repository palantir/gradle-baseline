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
import com.google.common.base.Preconditions;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "`Stream.filter(Optional::isPresent).map(Optional::get)` is more efficient than "
                + "`Stream.flatMap(Optional::stream)`")
public final class StreamFlatMapOptional extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAM_FLAT_MAP = MethodMatchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .namedAnyOf("flatMap")
            .withParameters(Function.class.getName());

    private static final Matcher<ExpressionTree> STREAMEX_FLAT_MAP = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .namedAnyOf("flatMap")
            .withParameters(Function.class.getName());

    private static final Matcher<ExpressionTree> ENTRY_STREAM_FLAT_MAP = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.EntryStream")
            .namedAnyOf("flatMapValues")
            .withParameters(Function.class.getName());

    private static final Matcher<ExpressionTree> OPTIONAL_STREAM = MethodMatchers.instanceMethod()
            .onDescendantOf(Optional.class.getName())
            .named("stream")
            .withNoParameters();

    private static final Matcher<ExpressionTree> STREAM_FLATMAP_OPTIONAL_STREAM = Matchers.methodInvocation(
            Matchers.anyOf(STREAM_FLAT_MAP, STREAMEX_FLAT_MAP, ENTRY_STREAM_FLAT_MAP),
            // Any of the three MatchTypes are reasonable in this case, given a single arg
            MatchType.LAST,
            OPTIONAL_STREAM);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<Optional<String>> test = List.of(Optional.of("test"));
        List<String> collect = test.stream().map(Optional::get).collect(Collectors.toList());
        Preconditions.checkState(!collect.isEmpty());
        if (STREAM_FLATMAP_OPTIONAL_STREAM.matches(tree, state)) {
            ExpressionTree receiver = ASTHelpers.getReceiver(tree.getMethodSelect());
            if (receiver != null) {
                SuggestedFix.Builder fix = SuggestedFix.builder();
                String optionalType = SuggestedFixes.qualifyType(state, fix, Optional.class.getCanonicalName());
                String replacement = ".filter(" + optionalType + "::isPresent).map(" + optionalType + "::get)";
                return buildDescription(tree)
                        .addFix(fix.replace(state.getEndPosition(receiver), state.getEndPosition(tree), replacement)
                                .build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }
}
