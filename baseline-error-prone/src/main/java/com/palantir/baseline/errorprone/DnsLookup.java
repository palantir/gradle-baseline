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
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.tree.JCTree;
import java.net.InetSocketAddress;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DnsLookup",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Calling 'new InetSocketAddress(host, port)' results in a DNS lookup which prevents the address "
                + "from following DNS changes in the future because it's already resolved. Additionally this is a "
                + "potential a network request, making the invocation significantly more expensive than expected "
                + "depending on the environment.\n"
                + "This check is intended to be advisory - it's fine to @SuppressWarnings(\"DnsLookup\") in"
                + " certain cases, but is usually not recommended.")
public final class DnsLookup extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final Matcher<ExpressionTree> INET_SOCKET_ADDRESS_MATCHER = MethodMatchers.constructor()
            .forClass(InetSocketAddress.class.getName())
            .withParameters(String.class.getName(), int.class.getName());

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (tree.getClassBody() == null && INET_SOCKET_ADDRESS_MATCHER.matches(tree, state)) {
            return buildDescription(tree)
                    // Suggested fix exists to provide context when compilation fails, it shouldn't be used
                    // as a drop in replacement because the unresolved string may not be sufficient in some
                    // cases.
                    .addFix(SuggestedFix.builder()
                            .replace(
                                    startPosition(tree),
                                    state.getEndPosition(tree.getIdentifier()),
                                    state.getSourceForNode(tree.getIdentifier()) + ".createUnresolved")
                            .build())
                    .build();
        }
        return Description.NO_MATCH;
    }

    private static int startPosition(ExpressionTree tree) {
        return ((JCTree) tree).getStartPosition();
    }
}
