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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Disallow usage of .collapseKeys() in EntryStream(s).")
public final class DangerousCollapseKeysUsage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;
    private static final String ERROR_MESSAGE = "The collapseKeys API of EntryStream should be avoided. The "
            + "API is frequently used as a grouping operation but its not suitable for that use case. The contract "
            + "requires duplicate keys to be adjacent to each other in the stream, which is rarely the case in "
            + "production code paths. When this constraint is violated, it leads to a duplicate key error at runtime.\n"
            + "A work around for the issue is to sort the keys prior to running the collapse operation. Since the "
            + "sort operation is surprising, a comment is often added to explain. Overall the usage of collapseKeys() "
            + "leads to code that is error prone or surprising.\nHence in place of collapseKeys() we recommend using "
            + "grouping operations which may require creation of intermediate maps but should avoid surprising code.";

    private static final String COLLAPSE_KEYS_METHOD = "collapseKeys";

    private static final Matcher<ExpressionTree> COLLAPSE_KEYS_CALL = MethodMatchers.instanceMethod()
            .onExactClass("one.util.streamex.EntryStream")
            .named(COLLAPSE_KEYS_METHOD);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!COLLAPSE_KEYS_CALL.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (usesSortedBeforeCollapseKeys(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
    }

    private boolean usesSortedBeforeCollapseKeys(MethodInvocationTree tree, VisitorState state) {
        // Fast check to avoid tokenizing the source if ".sorted()" is not used at all.
        String sourceForNode = state.getSourceForNode(tree);
        if (sourceForNode != null && !sourceForNode.contains(".sorted()")) {
            return false;
        }

        List<String> tokenIdentifiers = state.getTokensForNode(tree).stream()
                .filter(token -> token.kind() == TokenKind.IDENTIFIER)
                .map(token -> token.name().toString())
                .collect(ImmutableList.toImmutableList());

        for (int i = 0; i < tokenIdentifiers.size(); i++) {
            if (tokenIdentifiers.get(i).equals(COLLAPSE_KEYS_METHOD)) {
                if (i == 0 || !tokenIdentifiers.get(i - 1).equals("sorted")) {
                    return false;
                }
            }
        }
        return true;
    }
}
