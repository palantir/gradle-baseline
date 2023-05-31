/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import java.util.Comparator;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Using Stream::min is more efficient than finding the first element of the sorted stream. "
                + "Stream::min performs a linear scan through the stream to find the smallest element.")
public final class SortedStreamFirstElement extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> STREAM_FIND_FIRST_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .named("findFirst")
            .withNoParameters();

    private static final Matcher<MethodInvocationTree> RECEIVER_OF_STREAM_SORTED_NO_PARAMS_MATCHER =
            Matchers.receiverOfInvocation(MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getName())
                    .named("sorted")
                    .withNoParameters());

    private static final Matcher<MethodInvocationTree> RECEIVER_OF_STREAM_SORTED_WITH_COMPARATOR_MATCHER =
            Matchers.receiverOfInvocation(MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getName())
                    .named("sorted")
                    .withParameters(Comparator.class.getName()));
    private static final Matcher<MethodInvocationTree> MATCHER = Matchers.allOf(
            STREAM_FIND_FIRST_MATCHER,
            Matchers.anyOf(
                    RECEIVER_OF_STREAM_SORTED_NO_PARAMS_MATCHER, RECEIVER_OF_STREAM_SORTED_WITH_COMPARATOR_MATCHER));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        ExpressionTree sorted = ASTHelpers.getReceiver(tree);
        if (sorted == null) {
            // Not expected.
            return Description.NO_MATCH;
        }
        MethodInvocationTree sortedTree = (MethodInvocationTree) sorted;
        ExpressionTree stream = ASTHelpers.getReceiver(sorted);
        if (stream == null) {
            // Not expected.
            return Description.NO_MATCH;
        }

        if (RECEIVER_OF_STREAM_SORTED_NO_PARAMS_MATCHER.matches(tree, state)) {
            return describeMatch(
                    tree,
                    SuggestedFix.builder()
                            .replace(
                                    ((JCMethodInvocation) tree).getStartPosition(),
                                    state.getEndPosition(tree),
                                    state.getSourceForNode(stream) + ".min(Comparator.naturalOrder())")
                            .addImport(Comparator.class.getCanonicalName())
                            .build());
        } else if (RECEIVER_OF_STREAM_SORTED_WITH_COMPARATOR_MATCHER.matches(tree, state)) {
            return describeMatch(
                    tree,
                    SuggestedFix.builder()
                            .replace(
                                    ((JCMethodInvocation) tree).getStartPosition(),
                                    state.getEndPosition(tree),
                                    state.getSourceForNode(stream) + ".min("
                                            + state.getSourceForNode(
                                                    sortedTree.getArguments().get(0)) + ")")
                            .build());
        }

        return Description.NO_MATCH;
    }
}
