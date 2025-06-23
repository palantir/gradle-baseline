/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import org.gradle.api.GradleException;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "GradleException should only be thrown by Gradle internal code. "
                + "Prefer throwing RuntimeException or another appropriate exception instead.")
public final class GradleExceptionUsage extends BugChecker implements BugChecker.ThrowTreeMatcher {
    private static final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();
    private static final Matcher<ExpressionTree> ERROR = Matchers.isSubtypeOf(GradleException.class);

    @Override
    public Description matchThrow(ThrowTree tree, VisitorState state) {
        ExpressionTree expression = tree.getExpression();
        if (!(expression instanceof NewClassTree newClassTree)) {
            return Description.NO_MATCH;
        }
        if (!ERROR.matches(newClassTree.getIdentifier(), state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree).addFix(generateFix(newClassTree, state)).build();
    }

    private static SuggestedFix generateFix(NewClassTree newClassTree, VisitorState state) {
        Type throwableType = ASTHelpers.getType(newClassTree.getIdentifier());

        if (!ASTHelpers.isSameType(throwableType, state.getTypeFromString(GradleException.class.getName()), state)) {
            return SuggestedFix.emptyFix();
        }

        List<? extends ExpressionTree> arguments = newClassTree.getArguments();
        if (arguments.isEmpty()) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String qualifiedName = SuggestedFixes.qualifyType(state, fix, RuntimeException.class.getName());
            return fix.replace(newClassTree.getIdentifier(), qualifiedName).build();
        }

        ExpressionTree firstArgument = arguments.get(0);
        if (ASTHelpers.isSameType(
                ASTHelpers.getResultType(firstArgument), state.getTypeFromString(String.class.getName()), state)) {
            return SuggestedFix.builder()
                    .replace(newClassTree.getIdentifier(), "RuntimeException")
                    .build();
        }

        return SuggestedFix.emptyFix();
    }
}
