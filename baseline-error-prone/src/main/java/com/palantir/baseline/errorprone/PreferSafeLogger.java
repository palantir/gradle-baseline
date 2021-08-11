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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferSafeLogger",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Prefer using type-safe safe-logging loggers rather than safety-oblivious implementations.")
public final class PreferSafeLogger extends BugChecker implements BugChecker.VariableTreeMatcher {

    private static final int MAX_SUPPORTED_ARGS = 10;
    private static final Matcher<ExpressionTree> SLF4J_METHOD =
            MethodMatchers.instanceMethod().onDescendantOf("org.slf4j.Logger");

    private static final Matcher<ExpressionTree> SLF4J_SAFE_METHODS = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .namedAnyOf(
                    "isTraceEnabled",
                    "isDebugEnabled",
                    "isInfoEnabled",
                    "isWarnEnabled",
                    "isErrorEnabled",
                    "trace",
                    "debug",
                    "info",
                    "warn",
                    "error");

    private static final Matcher<VariableTree> MATCHER = Matchers.allOf(
            Matchers.isSubtypeOf("org.slf4j.Logger"),
            Matchers.variableInitializer(MethodMatchers.staticMethod()
                    .onClass("org.slf4j.LoggerFactory")
                    .named("getLogger")));

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        Symbol.VarSymbol sym = ASTHelpers.getSymbol(tree);
        AtomicBoolean foundUnknownUsage = new AtomicBoolean();
        SuggestedFix.Builder fix = SuggestedFix.builder();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree tree, Void _unused) {
                if (sym.equals(ASTHelpers.getSymbol(tree))) {
                    foundUnknownUsage.set(true);
                }
                return super.visitIdentifier(tree, null);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree tree, Void _unused) {
                if (sym.equals(ASTHelpers.getSymbol(tree))) {
                    foundUnknownUsage.set(true);
                }
                return super.visitMemberSelect(tree, null);
            }

            @Override
            public Void visitMethodInvocation(MethodInvocationTree tree, Void _unused) {
                if (SLF4J_METHOD.matches(tree, state)) {
                    ExpressionTree receiver = ASTHelpers.getReceiver(tree);
                    if (sym.equals(ASTHelpers.getSymbol(receiver))) {
                        if (!isSafeSlf4jInteraction(tree, fix, state)) {
                            foundUnknownUsage.set(true);
                        } else {
                            // Scan arguments for findings that may not compile
                            scan(tree.getArguments(), null);
                        }
                        return null;
                    }
                }
                return super.visitMethodInvocation(tree, null);
            }
        }.scan(state.getPath().getCompilationUnit(), null);
        if (foundUnknownUsage.get()) {
            return Description.NO_MATCH;
        }
        String qualifiedLogger = SuggestedFixes.qualifyType(state, fix, "com.palantir.logsafe.logger.SafeLogger");
        String qualifiedFactory =
                SuggestedFixes.qualifyType(state, fix, "com.palantir.logsafe.logger.SafeLoggerFactory");
        fix.replace(tree.getType(), qualifiedLogger);
        MethodInvocationTree initializerInvocation = (MethodInvocationTree) tree.getInitializer();
        fix.replace(initializerInvocation.getMethodSelect(), qualifiedFactory + ".get");
        return buildDescription(tree).addFix(fix.build()).build();
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static boolean isSafeSlf4jInteraction(
            MethodInvocationTree tree, SuggestedFix.Builder fix, VisitorState state) {
        if (!SLF4J_SAFE_METHODS.matches(tree, state)) {
            return false;
        }
        List<? extends Tree> arguments = tree.getArguments();
        if (arguments.isEmpty()) {
            // is<level>Enabled
            return true;
        }
        if (!state.getTypes()
                .isSameType(ASTHelpers.getType(arguments.get(0)), state.getTypeFromString(String.class.getName()))) {
            return false;
        }
        Type argType = state.getTypeFromString("com.palantir.logsafe.Arg");
        Type throwableType = state.getTypeFromString(Throwable.class.getName());
        int args = 0;
        int firstArgStartPosition = -1;
        int lastArgEndPosition = -1;
        for (int i = 1; i < arguments.size(); i++) {
            Tree argument = arguments.get(i);
            Type type = ASTHelpers.getType(argument);
            boolean isArg = ASTHelpers.isSubtype(type, argType, state);
            if (isArg) {
                if (i == 1) {
                    firstArgStartPosition = ASTHelpers.getStartPosition(argument);
                }
                args++;
                lastArgEndPosition = state.getEndPosition(argument);
            }
            boolean valid = isArg
                    // Throwable is valid only in the last position
                    || (ASTHelpers.isSubtype(type, throwableType, state) && i == arguments.size() - 1);
            if (!valid) {
                return false;
            }
        }
        if (args > MAX_SUPPORTED_ARGS) {
            // Update the call to wrap args with 'Arrays.asList' so the result will compile.
            CharSequence argsSource = state.getSourceCode().subSequence(firstArgStartPosition, lastArgEndPosition);
            String qualifiedArrays = SuggestedFixes.qualifyType(state, fix, Arrays.class.getName());
            fix.replace(
                    firstArgStartPosition,
                    lastArgEndPosition,
                    String.format("%s.asList(%s)", qualifiedArrays, argsSource));
        }

        return true;
    }
}
