/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Logger call interpolation markers should not be used for the throwable parameter because they "
                + "prevent stack traces from being logged in favor of the string value of the Throwable.")
public final class LoggerInterpolationConsumesThrowable extends BugChecker implements MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> LOG_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOfAny("org.slf4j.Logger", "com.palantir.logsafe.logger.SafeLogger")
            .namedAnyOf("trace", "debug", "info", "warn", "error", "fatal");

    private static final Matcher<ExpressionTree> MARKER = MoreMatchers.isSubtypeOf("org.slf4j.Marker");
    private static final Matcher<ExpressionTree> THROWABLE = MoreMatchers.isSubtypeOf(Throwable.class);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!LOG_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        // No need to verify logging calls with only a message
        if (args.size() <= 1) {
            return Description.NO_MATCH;
        }

        // Not problematic unless slf4j curly braces consume a throwable, so we can short-circuit out.
        int throwableIndex = args.size() - 1;
        ExpressionTree lastArg = args.get(throwableIndex);
        if (!THROWABLE.matches(lastArg, state)) {
            return Description.NO_MATCH;
        }

        boolean hasMarker = MARKER.matches(tree.getArguments().get(0), state);
        int messageIndex = hasMarker ? 1 : 0;
        ExpressionTree messageArg = args.get(messageIndex);

        if (messageArg.getKind() != Tree.Kind.STRING_LITERAL) {
            return Description.NO_MATCH;
        }

        String literalMessage = (String) ((LiteralTree) messageArg).getValue();
        int stringPlaceholders = countPlaceholders(literalMessage);
        int nonThrowableParameters = throwableIndex - messageIndex - 1;
        if (stringPlaceholders <= nonThrowableParameters) {
            return Description.NO_MATCH;
        }
        int extraPlaceholders = stringPlaceholders - nonThrowableParameters;
        return buildDescription(tree)
                .setMessage(String.format(
                        "Please remove %d '{}' placeholder%s. Logging statement contains %d placeholders for %d "
                                + "parameters. The Throwable will be consumed as a parameter (string value) rather "
                                + "than producing a stack trace.",
                        extraPlaceholders,
                        extraPlaceholders == 1 ? "" : "s",
                        stringPlaceholders,
                        nonThrowableParameters))
                .build();
    }

    private int countPlaceholders(String formatString) {
        int placeholders = 0;
        int index = 0;
        while (true) {
            index = formatString.indexOf("{}", index);
            if (index < 0) {
                return placeholders;
            }
            index++;
            placeholders++;
        }
    }
}
