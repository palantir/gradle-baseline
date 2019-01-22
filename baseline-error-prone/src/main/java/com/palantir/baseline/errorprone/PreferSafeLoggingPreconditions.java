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
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferSafeLoggingPreconditions",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        category = BugPattern.Category.ONE_OFF,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Precondition and similar checks with a constant message and no parameters should use equivalent "
                + "checks from com.palantir.logsafe.Preconditions for standardization as functionality is the same.")
public final class PreferSafeLoggingPreconditions extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    private static final String LOGSAFE_NAME = "com.palantir.logsafe.Preconditions";
    private static final Matcher<ExpressionTree> PRECONDITIONS_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.base.Preconditions")
            .withNameMatching(Pattern.compile("checkArgument|checkState|checkNotNull"));
    private static final Matcher<ExpressionTree> OBJECTS_MATCHER = MethodMatchers.staticMethod()
            .onClass("java.util.Objects")
            .withNameMatching(Pattern.compile("requireNonNull"));

    private static final String DESCRIPTION_MESSAGE =
            "The call can be replaced with an equivalent one from com.palantir.logsafe.Preconditions for "
                    + "standardization as the functionality is the same.";

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }

        boolean matchPreconditions = PRECONDITIONS_MATCHER.matches(tree, state);
        boolean matchObjects = OBJECTS_MATCHER.matches(tree, state);

        if (!matchPreconditions && !matchObjects) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (args.size() > 2) {
            return Description.NO_MATCH;
        }

        if (args.size() == 2) {
            ExpressionTree messageArg = args.get(1);
            boolean isStringType = ASTHelpers.isSameType(
                    ASTHelpers.getType(messageArg),
                    state.getTypeFromString("java.lang.String"),
                    state);
            if (!isStringType || !compileTimeConstExpressionMatcher.matches(messageArg, state)) {
                return Description.NO_MATCH;
            }
        }

        Description.Builder description = buildDescription(tree).setMessage(DESCRIPTION_MESSAGE);
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedClassName = SuggestedFixes.qualifyType(state, fix, LOGSAFE_NAME);
        if (matchPreconditions) {
            String replacement = String.format("%s.%s", qualifiedClassName, ASTHelpers.getSymbol(tree).name);
            fix.replace(tree.getMethodSelect(), replacement);
            return description.addFix(fix.build()).build();
        } else {
            String replacement = String.format("%s.%s", qualifiedClassName, "checkNotNull");
            fix.replace(tree.getMethodSelect(), replacement);
            return description.addFix(fix.build()).build();
        }
    }
}
