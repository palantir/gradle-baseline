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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

@AutoService(BugChecker.class)
@BugPattern(
        name = "CatchBlockLogException",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "log statement in catch block does not log the caught exception.")
public final class CatchBlockLogException extends BugChecker implements BugChecker.CatchTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> logMethod = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .withNameMatching(Pattern.compile("trace|debug|info|warn|error"));

    private static final Matcher<Tree> containslogMethod =
            Matchers.contains(Matchers.toType(ExpressionTree.class, logMethod));

    private static final Matcher<ExpressionTree> logException = Matchers.methodInvocation(
            logMethod, ChildMultiMatcher.MatchType.LAST, MoreMatchers.isSubtypeOf(Throwable.class));

    private static final Matcher<Tree> containslogException =
            Matchers.contains(Matchers.toType(ExpressionTree.class, logException));

    @Override
    public Description matchCatch(CatchTree tree, VisitorState state) {
        if (containslogMethod.matches(tree, state) && !containslogException.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(attemptFix(tree, state))
                    .setMessage("Catch block contains log statements but thrown exception is never logged.")
                    .build();
        }
        return Description.NO_MATCH;
    }

    private static Optional<SuggestedFix> attemptFix(CatchTree tree, VisitorState state) {
        List<MethodInvocationTree> matchingLoggingStatements =
                tree.getBlock().accept(LogStatementScanner.INSTANCE, state);
        if (matchingLoggingStatements == null || matchingLoggingStatements.size() != 1) {
            return Optional.empty();
        }
        MethodInvocationTree loggingInvocation = matchingLoggingStatements.get(0);
        if (containslogException.matches(loggingInvocation, state)) {
            return Optional.empty();
        }
        List<? extends ExpressionTree> loggingArguments = loggingInvocation.getArguments();
        // There are no valid log invocations without at least a single argument.
        ExpressionTree lastArgument = loggingArguments.get(loggingArguments.size() - 1);
        return Optional.of(SuggestedFix.builder()
                .replace(
                        lastArgument,
                        lastArgument
                                .accept(ThrowableFromArgVisitor.INSTANCE, state)
                                .orElseGet(() -> state.getSourceForNode(lastArgument)
                                        + ", "
                                        + tree.getParameter().getName()))
                .build());
    }

    private static final class ThrowableFromArgVisitor extends SimpleTreeVisitor<Optional<String>, VisitorState> {
        private static final ThrowableFromArgVisitor INSTANCE = new ThrowableFromArgVisitor();

        private static final Matcher<ExpressionTree> throwableMessageInvocation = Matchers.instanceMethod()
                .onDescendantOf(Throwable.class.getName())
                .named("getMessage");

        ThrowableFromArgVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<String> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (throwableMessageInvocation.matches(node, state)) {
                return node.getMethodSelect().accept(ThrowableFromInvocationVisitor.INSTANCE, state);
            }
            return Optional.empty();
        }
    }

    private static final class ThrowableFromInvocationVisitor
            extends SimpleTreeVisitor<Optional<String>, VisitorState> {
        private static final ThrowableFromInvocationVisitor INSTANCE = new ThrowableFromInvocationVisitor();

        ThrowableFromInvocationVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<String> visitMemberSelect(MemberSelectTree node, VisitorState state) {
            if (node.getIdentifier().contentEquals("getMessage")) {
                return Optional.ofNullable(state.getSourceForNode(node.getExpression()));
            }
            return Optional.empty();
        }
    }

    private static final class LogStatementScanner extends TreeScanner<List<MethodInvocationTree>, VisitorState> {
        private static final LogStatementScanner INSTANCE = new LogStatementScanner();

        @Override
        public List<MethodInvocationTree> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (logMethod.matches(node, state)) {
                return ImmutableList.of(node);
            }
            return super.visitMethodInvocation(node, state);
        }

        @Override
        public List<MethodInvocationTree> visitCatch(CatchTree node, VisitorState state) {
            // Do not flag logging from a nested catch, it's handled separately
            return ImmutableList.of();
        }

        @Override
        public List<MethodInvocationTree> reduce(
                @Nullable List<MethodInvocationTree> left, @Nullable List<MethodInvocationTree> right) {
            // Unfortunately there's no way to provide default initial values, so we must handle nulls.
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return ImmutableList.<MethodInvocationTree>builder()
                    .addAll(left)
                    .addAll(right)
                    .build();
        }
    }
}
