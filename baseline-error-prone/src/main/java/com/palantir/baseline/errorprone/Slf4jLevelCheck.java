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
import com.google.common.base.CaseFormat;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.IfTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import java.util.Locale;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Slf4jLevelCheck",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Slf4j log.is[Level]Enabled level must match the most severe log statement")
public final class Slf4jLevelCheck extends BugChecker implements IfTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> LEVEL_CHECK_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .namedAnyOf("isTraceEnabled", "isDebugEnabled", "isInfoEnabled", "isWarnEnabled", "isErrorEnabled");

    private static final Matcher<ExpressionTree> LOG_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .namedAnyOf("trace", "debug", "info", "warn", "error");

    @Override
    public Description matchIf(IfTree tree, VisitorState state) {
        if (tree.getElseStatement() != null) {
            // If a level check has an else statement, it's more complicated than the standard fare. We avoid
            // checking it to keep signal high, these haven't caused enough problems to necessitate becoming
            // prescriptive.
            return Description.NO_MATCH;
        }
        // n.b. This check does not validate that the level check and logging occur on the same logger instance.
        // It's possible to have multiple loggers in the same class used for different purposes, however we recommend
        // against it.
        Optional<MethodInvocationTree> maybeCheckLevel = tree.getCondition().accept(ConditionVisitor.INSTANCE, state);
        if (!maybeCheckLevel.isPresent()) {
            return Description.NO_MATCH;
        }
        MethodInvocationTree levelCheckInvocation = maybeCheckLevel.get();
        LogLevel checkLevel = levelCheckLogLevel(levelCheckInvocation, state);
        LogLevel mostSevere = tree.getThenStatement().accept(MostSevereLogStatementScanner.INSTANCE, state);
        if (mostSevere == null) {
            // Unable to find logging in this tree. This call likely delegates to something else which logs,
            // but we cannot detect it.
            return Description.NO_MATCH;
        }
        if (mostSevere == checkLevel) {
            // The check matches the most severe log statement level. Keep up the great work!
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .addFix(MoreSuggestedFixes.renameMethodInvocation(
                        levelCheckInvocation, mostSevere.levelCheckMethodName(), state))
                .build();
    }

    @SuppressWarnings("PreferSafeLoggableExceptions")
    private static LogLevel levelCheckLogLevel(MethodInvocationTree tree, VisitorState state) {
        for (LogLevel level : LogLevel.values()) {
            if (level.matchesLevelCheck(tree, state)) {
                return level;
            }
        }
        throw new IllegalStateException("Expected a level check, but was: " + state.getSourceForNode(tree));
    }

    private static final class ConditionVisitor
            extends SimpleTreeVisitor<Optional<MethodInvocationTree>, VisitorState> {

        private static final ConditionVisitor INSTANCE = new ConditionVisitor();

        private ConditionVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<MethodInvocationTree> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (LEVEL_CHECK_METHOD.matches(node, state)) {
                return Optional.of(node);
            }
            return Optional.empty();
        }

        @Override
        public Optional<MethodInvocationTree> visitParenthesized(ParenthesizedTree node, VisitorState state) {
            return node.getExpression().accept(this, state);
        }

        // It's relatively common to do a quick check (often equality or null check) along with a level check.
        // We only support expressions with a single level check matched with '&&' to keep things simple, as this
        // is the most common case.
        @Override
        public Optional<MethodInvocationTree> visitBinary(BinaryTree node, VisitorState state) {
            if (node.getKind() != Tree.Kind.CONDITIONAL_AND) {
                return Optional.empty();
            }
            Optional<MethodInvocationTree> lhs = node.getLeftOperand().accept(this, state);
            Optional<MethodInvocationTree> rhs = node.getRightOperand().accept(this, state);
            // If there are level checks on both sides, bail
            if (lhs.isPresent() && rhs.isPresent()) {
                return Optional.empty();
            }
            return lhs.isPresent() ? lhs : rhs;
        }
    }

    private static final class MostSevereLogStatementScanner extends TreeScanner<LogLevel, VisitorState> {
        private static final MostSevereLogStatementScanner INSTANCE = new MostSevereLogStatementScanner();

        @Override
        public LogLevel visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (LOG_METHOD.matches(node, state)) {
                for (LogLevel level : LogLevel.values()) {
                    if (level.matchesLogStatement(node, state)) {
                        return level;
                    }
                }
            }
            return null;
        }

        @Override
        public LogLevel visitCatch(CatchTree node, VisitorState state) {
            // Do not flag logging from a catch withing a level-check conditional. These are sometimes
            // more severe if there's a problem generating fine grained logging.
            return null;
        }

        @Override
        public LogLevel reduce(LogLevel r1, LogLevel r2) {
            if (r1 == null) {
                return r2;
            }
            if (r2 == null) {
                return r1;
            }
            return r1.ordinal() > r2.ordinal() ? r1 : r2;
        }
    }

    @SuppressWarnings("unused")
    private enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR;

        private final String levelCheckMethodName =
                "is" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name()) + "Enabled";

        @SuppressWarnings("ImmutableEnumChecker")
        private final Matcher<ExpressionTree> levelCheckMatcher = MethodMatchers.instanceMethod()
                .onDescendantOf("org.slf4j.Logger")
                .named(levelCheckMethodName);

        @SuppressWarnings("ImmutableEnumChecker")
        private final Matcher<ExpressionTree> logMatcher = MethodMatchers.instanceMethod()
                .onDescendantOf("org.slf4j.Logger")
                .named(name().toLowerCase(Locale.ENGLISH));

        boolean matchesLevelCheck(ExpressionTree tree, VisitorState state) {
            return levelCheckMatcher.matches(tree, state);
        }

        boolean matchesLogStatement(ExpressionTree tree, VisitorState state) {
            return logMatcher.matches(tree, state);
        }

        String levelCheckMethodName() {
            return levelCheckMethodName;
        }
    }
}
