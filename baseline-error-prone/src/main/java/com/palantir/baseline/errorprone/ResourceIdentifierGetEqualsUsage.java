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
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import javax.lang.model.element.Name;

@AutoService(BugChecker.class)
@BugPattern(
        summary = "Disallowed usage of ResourceIdentifier#get{Instance,Locator,Service,Type}#equals",
        explanation = "ResourceIdentifier internally stores a single string for the entire RID. Each of the getX "
                + "methods allocates a new string for that specific part of the RID. Use "
                + "ResourceIdentifier#has{Instance,Locator,Service,Type} instead, which does not allocate any memory.",
        severity = SeverityLevel.WARNING,
        linkType = BugPattern.LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks")
public final class ResourceIdentifierGetEqualsUsage extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> EQUALS_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(String.class.getName())
            .named("equals");
    private static final Matcher<ExpressionTree> GET_MATCHER = MethodMatchers.instanceMethod()
            .onExactClass("com.palantir.ri.ResourceIdentifier")
            .namedAnyOf("getInstance", "getLocator", "getService", "getType");

    private static final Matcher<MethodInvocationTree> GET_RECEIVER_MATCHER =
            Matchers.receiverOfInvocation(GET_MATCHER);
    private static final Matcher<MethodInvocationTree> GET_ARGUMENT_MATCHER = Matchers.argument(0, GET_MATCHER);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!EQUALS_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (GET_RECEIVER_MATCHER.matches(tree, state)) {
            ExpressionTree ridTree = ASTHelpers.getReceiver(tree);
            ExpressionTree valueTree = tree.getArguments().get(0);

            return buildDescription(tree)
                    .addFix(getSuggestedFix(tree, state, ridTree, valueTree))
                    .build();
        } else if (GET_ARGUMENT_MATCHER.matches(tree, state)) {
            ExpressionTree ridTree = tree.getArguments().get(0);
            ExpressionTree valueTree = ASTHelpers.getReceiver(tree);

            return buildDescription(tree)
                    .addFix(getSuggestedFix(tree, state, ridTree, valueTree))
                    .build();
        } else {
            return Description.NO_MATCH;
        }
    }

    private static SuggestedFix getSuggestedFix(
            MethodInvocationTree tree, VisitorState state, ExpressionTree getTree, ExpressionTree valueTree) {
        ExpressionTree ridTree = ASTHelpers.getReceiver(getTree);

        Name methodName = ((MemberSelectTree) ((MethodInvocationTree) getTree).getMethodSelect()).getIdentifier();

        return SuggestedFix.builder()
                .replace(
                        tree,
                        state.getSourceForNode(ridTree) + ".has" + methodName.subSequence(3, methodName.length()) + "("
                                + state.getSourceForNode(valueTree) + ")")
                .build();
    }
}
