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
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ThrowError",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary =
                "Prefer throwing a RuntimeException rather than Error. Errors are often handled poorly by libraries"
                    + " resulting in unexpected behavior and resource leaks. It's not obvious that 'catch (Exception"
                    + " e)' does not catch Error.\n"
                    + "Errors are normally thrown by the JVM when the system, not just the application, is in a bad"
                    + " state. For example, LinkageError is thrown by the JVM when it encounters incompatible classes,"
                    + " and NoClassDefFoundError when a class cannot be found. These should be less common and handled"
                    + " differently from application failures.\n"
                    + "This check  is intended to be advisory - it's fine to @SuppressWarnings(\"ThrowError\") in"
                    + " certain cases, but is usually not recommended unless you are writing a testing library that"
                    + " throws AssertionError.")
public final class ThrowError extends BugChecker implements BugChecker.ThrowTreeMatcher {

    private static final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();
    private static final Matcher<ExpressionTree> ERROR = MoreMatchers.isSubtypeOf(Error.class);

    @Override
    public Description matchThrow(ThrowTree tree, VisitorState state) {
        ExpressionTree expression = tree.getExpression();
        if (!(expression instanceof NewClassTree)) {
            return Description.NO_MATCH;
        }
        NewClassTree newClassTree = (NewClassTree) expression;
        if (!ERROR.matches(newClassTree.getIdentifier(), state)
                // Don't discourage developers from testing edge cases involving Errors.
                // It's also fine for tests throw AssertionError internally in test objects.
                || TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree).addFix(generateFix(newClassTree, state)).build();
    }

    private static Optional<SuggestedFix> generateFix(NewClassTree newClassTree, VisitorState state) {
        Type throwableType = ASTHelpers.getType(newClassTree.getIdentifier());
        // AssertionError is the most common failure case we've encountered, likely because it sounds
        // similar to IllegalStateException. In this case we suggest replacing it with IllegalStateException.
        if (!ASTHelpers.isSameType(throwableType, state.getTypeFromString(AssertionError.class.getName()), state)) {
            return Optional.empty();
        }
        List<? extends ExpressionTree> arguments = newClassTree.getArguments();
        if (arguments.isEmpty()) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String qualifiedName = MoreSuggestedFixes.qualifyType(state, fix, IllegalStateException.class.getName());
            return Optional.of(
                    fix.replace(newClassTree.getIdentifier(), qualifiedName).build());
        }
        ExpressionTree firstArgument = arguments.get(0);
        if (ASTHelpers.isSameType(
                ASTHelpers.getResultType(firstArgument), state.getTypeFromString(String.class.getName()), state)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String qualifiedName = MoreSuggestedFixes.qualifyType(
                    state,
                    fix,
                    compileTimeConstExpressionMatcher.matches(firstArgument, state)
                            ? "com.palantir.logsafe.exceptions.SafeIllegalStateException"
                            : IllegalStateException.class.getName());
            return Optional.of(
                    fix.replace(newClassTree.getIdentifier(), qualifiedName).build());
        }
        return Optional.empty();
    }
}
