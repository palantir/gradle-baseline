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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Use Optional::orElseGet instead of Optional::orElse. TODO(pritham).")
public final class OptionalOrElseGet extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<ExpressionTree> OR_ELSE_MATCHER = Matchers.instanceMethod()
            .onDescendantOf(Optional.class.getName())
            .namedAnyOf("orElse")
            .withParameters(Object.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!OR_ELSE_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (tree.getArguments().size() != 1) {
            return Description.NO_MATCH;
        }
        ExpressionTree arg = tree.getArguments().get(0);

        return buildDescription(tree)
                .addFix(SuggestedFix.builder()
                        .replace(
                                tree,
                                state.getSourceForNode(ASTHelpers.getReceiver(tree)) + ".orElseGet(() -> "
                                        + state.getSourceForNode(arg) + ")")
                        .build())
                .build();
    }
}
