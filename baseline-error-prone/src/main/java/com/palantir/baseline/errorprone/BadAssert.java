/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.code.Type;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Assert statements should not be used. Asserts are generally enabled in tests but disabled in "
                + "production, which means code can execute in production that is impossible to test.\n"
                + "If you're confident that an 'assert' is required, this check may be suppressed using "
                + "@SuppressWarnings(\"BadAssert\")")
public final class BadAssert extends BugChecker implements BugChecker.AssertTreeMatcher {

    private static final String LOGSAFE_PRECONDITIONS = "com.palantir.logsafe.Preconditions";
    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    @Override
    public Description matchAssert(AssertTree tree, VisitorState state) {
        ExpressionTree condition = tree.getCondition();
        ExpressionTree detail = tree.getDetail();
        SuggestedFix.Builder fix = SuggestedFix.builder();
        if (detail == null) {
            fix.replace(
                    tree,
                    String.format(
                            "if (!%s) { throw new IllegalStateException(); }", state.getSourceForNode(condition)));
        } else if (isString(ASTHelpers.getType(detail), state)
                && compileTimeConstExpressionMatcher.matches(detail, state)) {
            fix.replace(
                    tree,
                    String.format(
                            "%s.checkState(%s, %s)",
                            SuggestedFixes.qualifyType(state, fix, LOGSAFE_PRECONDITIONS),
                            state.getSourceForNode(condition),
                            state.getSourceForNode(detail)));
        } else {
            String message = isString(ASTHelpers.getType(detail), state)
                    ? state.getSourceForNode(detail)
                    : "String.valueOf(" + state.getSourceForNode(detail) + ")";
            fix.replace(
                    tree,
                    String.format(
                            "if (!%s) { throw new IllegalStateException(%s); }",
                            state.getSourceForNode(condition), message));
        }
        return buildDescription(tree).addFix(fix.build()).build();
    }

    private static boolean isString(Type type, VisitorState state) {
        return state.getTypes().isSameType(type, state.getTypeFromString(String.class.getName()));
    }
}
