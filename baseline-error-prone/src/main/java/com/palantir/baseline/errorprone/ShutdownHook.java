/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.predicates.TypePredicates;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ShutdownHook",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "use your webserver's managed resource functionality instead of "
                + "using Runtime#addShutdownHook directly.")
public final class ShutdownHook extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;
    private static final String errorMsg = "Use your webserver's managed resource functionality "
            + "instead of using java.lang.Runtime#addShutdownHook directly.";

    private static final Matcher<ExpressionTree> shutdownHookMethods = MethodMatchers.instanceMethod()
            .onClass(TypePredicates.isExactType("java.lang.Runtime"))
            .withNameMatching(Pattern.compile("addShutdownHook|removeShutdownHook"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!shutdownHookMethods.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).setMessage(errorMsg).build();
    }
}
