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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Slf4jThrowable",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary =
                "Slf4j loggers require throwables to be the last parameter otherwise a stack trace is not produced. "
                        + "Documentation is available here: http://www.slf4j.org/faq.html#paramException")
public final class Slf4jThrowable extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> THROWABLE = MoreMatchers.isSubtypeOf(Throwable.class);

    private static final Matcher<ExpressionTree> LOG_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .withNameMatching(Pattern.compile("trace|debug|info|warn|error"));

    private static final Matcher<ExpressionTree> CORRECT_THROWABLE =
            Matchers.methodInvocation(LOG_METHOD, ChildMultiMatcher.MatchType.LAST, THROWABLE);

    private static final Matcher<ExpressionTree> ANY_THROWABLE =
            Matchers.methodInvocation(LOG_METHOD, ChildMultiMatcher.MatchType.AT_LEAST_ONE, THROWABLE);

    private static final Matcher<ExpressionTree> MATCHER =
            Matchers.allOf(Matchers.not(CORRECT_THROWABLE), ANY_THROWABLE);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!ANY_THROWABLE.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        List<? extends ExpressionTree> arguments = tree.getArguments();
        int lastIndex = arguments.size() - 1;
        int throwableIndex = getFirstThrowableArgumentIndex(arguments, state);
        if (throwableIndex == lastIndex) {
            // Correct usage
            return Description.NO_MATCH;
        }
        if (countThrowableArguments(arguments, state) > 1) {
            // We cannot fix cases with multiple throwables.
            return describeMatch(arguments.get(throwableIndex));
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        for (int i = throwableIndex; i < lastIndex; i++) {
            fix.replace(arguments.get(i), state.getSourceForNode(arguments.get(i + 1)));
        }
        ExpressionTree throwableArgument = arguments.get(throwableIndex);
        return buildDescription(throwableArgument)
                .addFix(fix.replace(arguments.get(lastIndex), state.getSourceForNode(throwableArgument))
                        .build())
                .build();
    }

    private static int getFirstThrowableArgumentIndex(List<? extends ExpressionTree> arguments, VisitorState state) {
        for (int i = 0; i < arguments.size(); i++) {
            ExpressionTree argument = arguments.get(i);
            if (THROWABLE.matches(argument, state)) {
                return i;
            }
        }
        throw new IllegalStateException("Failed to find a throwable argument");
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    private static int countThrowableArguments(List<? extends ExpressionTree> arguments, VisitorState state) {
        int count = 0;
        for (int i = 0; i < arguments.size(); i++) {
            if (THROWABLE.matches(arguments.get(i), state)) {
                count++;
            }
        }
        return count;
    }
}
