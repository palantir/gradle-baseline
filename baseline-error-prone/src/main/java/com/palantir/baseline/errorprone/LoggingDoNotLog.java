/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.predicates.TypePredicates;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnalysis;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;

/**
 * Ensures that data which has been marked as {@code @DoNotLog} is not passed to a logger, or an exceptoin which
 * will almost certainly be passed to a logger.
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "@DoNotLog types must not be passed to any logger directly or indirectly, for example respectively: "
                + "log.info(doNotLog) or throw new RuntimeException(doNotLog.toString()). Exceptions are almost "
                + "always passed to a logger in some form, and must not include data that cannot be logged.")
public final class LoggingDoNotLog extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.NewClassTreeMatcher {

    private static final Matcher<ExpressionTree> THROWABLE_CTOR = MethodMatchers.constructor()
            .forClass(TypePredicates.allOf(
                    TypePredicates.isDescendantOf(Throwable.class.getName()),
                    // Avoid double-checking safe-loggable implementations which are handled by
                    // IllegalSafeLoggingArgument
                    TypePredicates.not(TypePredicates.isDescendantOf("com.palantir.logsafe.SafeLoggable"))));

    // Note that we don't check SafeLogger here because it is handled by the IllegalSafeLoggingArgument check.
    private static final Matcher<ExpressionTree> LOGGING_METHODS = Matchers.anyOf(
            MethodMatchers.instanceMethod().onDescendantOf("org.slf4j.Logger"),
            MethodMatchers.instanceMethod().onDescendantOf("org.apache.log4j.Logger"),
            MethodMatchers.instanceMethod().onDescendantOf("org.apache.logging.log4j.Logger"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(System.Logger.class.getName())
                    .named("log"),
            MethodMatchers.instanceMethod().onDescendantOf("java.util.logging.Logger"),
            // MDC interactions result in data in the logs
            MethodMatchers.staticMethod().onClass("org.slf4j.MDC"),
            MethodMatchers.staticMethod().onClass("org.apache.log4j.MDC"),
            MethodMatchers.staticMethod().onClass("org.apache.logging.log4j.ThreadContext"));

    // Result in indirect logging
    private static final Matcher<ExpressionTree> PRECONDITIONS_METHODS = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.base.Preconditions")
                    .namedAnyOf("checkArgument", "checkNotNull", "checkState"),
            MethodMatchers.staticMethod()
                    .onClass(Objects.class.getName())
                    .named("requireNonNull")
                    .withParameters(Object.class.getName(), String.class.getName()),
            MethodMatchers.staticMethod()
                    .onClass("org.apache.commons.lang3.Validate")
                    .namedAnyOf("isTrue", "notNull", "validState"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<? extends ExpressionTree> arguments = tree.getArguments();
        if (arguments.isEmpty()) {
            return Description.NO_MATCH;
        }
        if (LOGGING_METHODS.matches(tree, state)) {
            checkArguments(arguments, state, 0);
        }
        // Avoid a relatively expensive check if there are too few arguments to provide an exception message
        if (arguments.size() > 1 && PRECONDITIONS_METHODS.matches(tree, state)) {
            checkArguments(arguments, state, 1);
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (!tree.getArguments().isEmpty() && THROWABLE_CTOR.matches(tree, state)) {
            checkArguments(tree.getArguments(), state, 0);
        }
        return Description.NO_MATCH;
    }

    private void checkArguments(List<? extends ExpressionTree> arguments, VisitorState state, int beginIndex) {
        for (int i = beginIndex; i < arguments.size(); i++) {
            ExpressionTree argument = arguments.get(i);
            Safety argumentSafety = SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), argument)));
            if (argumentSafety == Safety.DO_NOT_LOG) {
                state.reportMatch(describeMatch(argument));
            }
        }
    }
}
