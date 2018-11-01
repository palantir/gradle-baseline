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
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousThrowableMessageSafeArg",
        category = Category.ONE_OFF,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "It is unsafe to create a SafeArg of Throwable.getMessage, SafeLoggable.getLogMessage may be used.")
public final class DangerousThrowableMessageSafeArg extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> SAFEARG_FACTORY_METHOD =
            Matchers.anyOf(
                    MethodMatchers.staticMethod()
                            .onClass("com.palantir.logsafe.SafeArg")
                            .named("of"));

    private static final Matcher<ExpressionTree> THROWABLE_MESSAGE_METHOD =
            Matchers.anyOf(
                    MethodMatchers.instanceMethod()
                            .onDescendantOf(Throwable.class.getName())
                            .named("getMessage"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!SAFEARG_FACTORY_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (args.size() != 2) {
            return Description.NO_MATCH;
        }

        ExpressionTree safeValueArgument = args.get(1);
        if (THROWABLE_MESSAGE_METHOD.matches(safeValueArgument, state)) {
            return buildDescription(tree)
                    .setMessage("Do not use throwable messages as SafeArg values. "
                            + "SafeLoggable.getLogMessage is guaranteed to be safe.")
                    .build();
        }
        return Description.NO_MATCH;
    }
}
