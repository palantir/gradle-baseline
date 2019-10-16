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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ReverseDnsLookup",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary =
                "Calling address.getHostName may result in a reverse DNS lookup which is a network request, making "
                        + "the invocation significantly more expensive than expected depending on the environment.\n"
                        + "This check is intended to be advisory - it's fine to @SuppressWarnings(\"ReverseDnsLookup\") "
                        + "in certain cases, but is usually not recommended.")
public final class ReverseDnsLookup extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> INET_SOCKET_ADDRESS_MATCHER =
            MethodMatchers.instanceMethod().onDescendantOf(InetSocketAddress.class.getName()).named("getHostName");

    private static final Matcher<ExpressionTree> INET_ADDRESS_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(InetAddress.class.getName())
            .namedAnyOf("getHostName", "getCanonicalHostName");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (INET_SOCKET_ADDRESS_MATCHER.matches(tree, state)) {
            return buildDescription(tree)
                    // Suggested fix exists to provide context when compilation fails, it shouldn't be used
                    // as a drop in replacement because the unresolved string may not be sufficient in some
                    // cases, particularly involving auditing.
                    .addFix(SuggestedFixes.renameMethodInvocation(tree, "getHostString", state))
                    .build();
        }
        if (INET_ADDRESS_MATCHER.matches(tree, state)) {
            return buildDescription(tree)
                    // Suggested fix exists to provide context when compilation fails, it shouldn't be used
                    // as a drop in replacement because the unresolved string may not be sufficient in some
                    // cases, particularly involving auditing.
                    .addFix(SuggestedFixes.renameMethodInvocation(tree, "getHostAddress", state))
                    .build();
        }
        return Description.NO_MATCH;
    }
}
