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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "CollectionStreamForEach",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Collection.forEach is more efficient than Collection.stream().forEach")
public final class CollectionStreamForEach extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAM_FOR_EACH = MethodMatchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .namedAnyOf("forEach", "forEachOrdered")
            .withParameters(Consumer.class.getName());

    private static final Matcher<ExpressionTree> COLLECTION_STREAM = MethodMatchers.instanceMethod()
            .onDescendantOf(Collection.class.getName())
            .named("stream")
            .withParameters();

    private static final Matcher<MethodInvocationTree> matcher =
            Matchers.allOf(STREAM_FOR_EACH, Matchers.receiverOfInvocation(COLLECTION_STREAM));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (matcher.matches(tree, state)) {
            ExpressionTree stream = ASTHelpers.getReceiver(tree);
            if (stream == null) {
                // Should be impossible.
                return describeMatch(tree);
            }
            ExpressionTree collection = ASTHelpers.getReceiver(stream);
            if (collection == null) {
                // Should be impossible.
                return describeMatch(tree);
            }
            return buildDescription(tree)
                    .addFix(SuggestedFix.builder()
                            // Replaces forEachOrdered with forEach
                            .merge(MoreSuggestedFixes.renameInvocationRetainingTypeArguments(tree, "forEach", state))
                            .replace(stream, state.getSourceForNode(collection))
                            .build())
                    .build();
        }
        return Description.NO_MATCH;
    }
}
