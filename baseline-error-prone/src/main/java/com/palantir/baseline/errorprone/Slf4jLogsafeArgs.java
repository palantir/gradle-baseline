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
import com.google.common.collect.ImmutableList;
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
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Slf4jLogsafeArgs",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "Allow only com.palantir.logsafe.Arg types, or vararg arrays, as parameter inputs to slf4j log "
                + "messages.")
public final class Slf4jLogsafeArgs extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> LOG_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .withNameMatching(Pattern.compile("trace|debug|info|warn|error"));

    private static final Matcher<ExpressionTree> THROWABLE = MoreMatchers.isSubtypeOf(Throwable.class);
    private static final Matcher<ExpressionTree> ARG = MoreMatchers.isSubtypeOf("com.palantir.logsafe.Arg");
    private static final Matcher<ExpressionTree> MARKER = MoreMatchers.isSubtypeOf("org.slf4j.Marker");
    private static final Matcher<Tree> OBJECT_ARRAY = Matchers.isSubtypeOf(
            s -> s.getType(s.getTypeFromString("java.lang.Object"), true, Collections.emptyList()));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!LOG_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<Description> wrappedThrowableDescription = checkThrowableArgumentNotWrapped(tree, state);
        if (wrappedThrowableDescription.isPresent()) {
            return wrappedThrowableDescription.get();
        }

        List<? extends ExpressionTree> allArgs = tree.getArguments();
        int startArgIndex = MARKER.matches(allArgs.get(0), state) ? 2 : 1;
        int lastArgIndex = allArgs.size() - 1;
        ExpressionTree lastArg = allArgs.get(lastArgIndex);

        if (startArgIndex == lastArgIndex && OBJECT_ARRAY.matches(lastArg, state)) {
            return Description.NO_MATCH;
        }

        boolean lastArgIsThrowable = THROWABLE.matches(lastArg, state);
        int lastNonThrowableArgIndex = lastArgIsThrowable ? lastArgIndex - 1 : lastArgIndex;

        List<Integer> badArgs = getBadArgIndices(state, allArgs, startArgIndex, lastNonThrowableArgIndex);
        if (badArgs.isEmpty() || TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("slf4j log statement does not use logsafe parameters for arguments " + badArgs)
                .build();
    }

    private List<Integer> getBadArgIndices(VisitorState state, List<? extends ExpressionTree> args, int from, int to) {
        ImmutableList.Builder<Integer> badArgsBuilder = ImmutableList.builder();
        for (int i = from; i <= to; i++) {
            if (!ARG.matches(args.get(i), state)) {
                badArgsBuilder.add(i);
            }
        }
        return badArgsBuilder.build();
    }

    private Optional<Description> checkThrowableArgumentNotWrapped(MethodInvocationTree tree, VisitorState state) {
        ExpressionTree lastArg = tree.getArguments().get(tree.getArguments().size() - 1);
        boolean lastArgIsThrowable = THROWABLE.matches(lastArg, state);
        if (lastArgIsThrowable) {
            return Optional.empty();
        }
        return lastArg.accept(ThrowableArgVisitor.INSTANCE, state).map(node -> buildDescription(tree)
                .setMessage("slf4j log statement should not use logsafe wrappers for throwables")
                .addFix(SuggestedFix.replace(lastArg, state.getSourceForNode(node)))
                .build());
    }

    /** Returns the throwable argument from SafeArg.of(name, throwable) or UnsafeArg.of(name, throwable). */
    private static final class ThrowableArgVisitor extends SimpleTreeVisitor<Optional<ExpressionTree>, VisitorState> {
        private static final ThrowableArgVisitor INSTANCE = new ThrowableArgVisitor();

        private static final Matcher<ExpressionTree> ARG_FACTORY = Matchers.staticMethod()
                .onClassAny("com.palantir.logsafe.SafeArg", "com.palantir.logsafe.UnsafeArg")
                .named("of")
                .withParameters(String.class.getName(), Object.class.getName());

        private static final Matcher<ExpressionTree> THROWABLE_ARG =
                Matchers.methodInvocation(ARG_FACTORY, ChildMultiMatcher.MatchType.LAST, THROWABLE);

        private ThrowableArgVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<ExpressionTree> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (THROWABLE_ARG.matches(node, state)) {
                return Optional.of(node.getArguments().get(1));
            }
            return Optional.empty();
        }
    }
}
