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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Precondition and similar checks with a constant message and no parameters should use equivalent"
                + " checks from com.palantir.logsafe.Preconditions for standardization as functionality is the"
                + " same.")
public final class PreferSafeLoggingPreconditions extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    private static final Matcher<ExpressionTree> PRECONDITIONS_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.base.Preconditions")
            .withNameMatching(Pattern.compile("checkArgument|checkState|checkNotNull"));

    private static final Matcher<ExpressionTree> METHOD_MATCHER = Matchers.anyOf(
            PRECONDITIONS_MATCHER,
            MethodMatchers.staticMethod().onClass("java.util.Objects").named("requireNonNull"),
            MethodMatchers.staticMethod()
                    .onClass("org.apache.commons.lang3.Validate")
                    .withNameMatching(Pattern.compile("isTrue|notNull|validState")));

    private static final Matcher<ExpressionTree> ARG_MATCHER = MoreMatchers.isSubtypeOf("com.palantir.logsafe.Arg");

    private static final ImmutableMap<String, String> TRANSLATIONS_TO_LOGSAFE_PRECONDITIONS_METHODS = ImmutableMap.of(
            "requireNonNull", "checkNotNull", // java.util.Objects.requireNotNull
            "isTrue", "checkArgument", // org.apache.commons.lang3.Validate.isTrue
            "notNull", "checkNotNull", // org.apache.commons.lang3.Validate.notNull
            "validState", "checkState"); // org.apache.commons.lang3.Validate.validState

    private static final Supplier<Type> JAVA_STRING =
            VisitorState.memoize(state -> state.getTypeFromString("java.lang.String"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!METHOD_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (args.size() >= 2) {
            ExpressionTree messageArg = args.get(1);
            boolean isStringType = ASTHelpers.isSameType(ASTHelpers.getType(messageArg), JAVA_STRING.get(state), state);
            if (!isStringType || !compileTimeConstExpressionMatcher.matches(messageArg, state)) {
                return Description.NO_MATCH;
            }
            return checkGuavaPreconditionsAndLogsafeArgMixing(tree, state, args);
        }

        return suggestFix(tree, state);
    }

    private Description checkGuavaPreconditionsAndLogsafeArgMixing(
            MethodInvocationTree tree, VisitorState state, List<? extends ExpressionTree> args) {
        boolean anyMatch = false;
        boolean allMatch = true;
        for (int i = 2; i < args.size(); i++) {
            ExpressionTree arg = args.get(i);
            if (ARG_MATCHER.matches(arg, state)) {
                anyMatch = true;
            } else {
                allMatch = false;
            }
        }
        if (allMatch) {
            return suggestFix(tree, state);
        } else if (anyMatch) {
            return buildDescription(tree)
                    .setMessage("An Arg was passed to Preconditions.checkX(), but not all. Convert the non-Args to"
                            + " be Args and use com.palantir.logsafe.Preconditions instead.")
                    .build();
        }
        return Description.NO_MATCH;
    }

    private Description suggestFix(MethodInvocationTree tree, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String logSafeQualifiedClassName = SuggestedFixes.qualifyType(state, fix, "com.palantir.logsafe.Preconditions");
        String logSafeMethodName = getLogSafeMethodName(ASTHelpers.getSymbol(tree));
        String replacement = String.format("%s.%s", logSafeQualifiedClassName, logSafeMethodName);

        return buildDescription(tree)
                .setMessage("The call can be replaced with an equivalent one from com.palantir.logsafe.Preconditions "
                        + "for standardization as the functionality is the same.")
                .addFix(fix.replace(tree.getMethodSelect(), replacement).build())
                .build();
    }

    private static String getLogSafeMethodName(MethodSymbol methodSymbol) {
        String name = methodSymbol.name.toString();
        return TRANSLATIONS_TO_LOGSAFE_PRECONDITIONS_METHODS.getOrDefault(name, name);
    }
}
