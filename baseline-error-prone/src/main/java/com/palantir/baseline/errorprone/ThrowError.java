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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ThrowError",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Prefer throwing a RuntimeException rather than Error. Errors are often handled poorly by libraries "
                + "resulting in unexpected behavior and resource leaks. It's not obvious that "
                + "'catch (Exception e)' does not catch Error.\n"
                + "Errors are normally thrown by the JVM when the system, not just the application, "
                + "is in a bad state. For example, LinkageError is thrown by the JVM when it encounters "
                + "incompatible classes, and OutOfMemoryError when allocations fail. These should be "
                + "less common and handled differently from application failures.\n"
                + "This check  is intended to be advisory - it's fine to @SuppressWarnings(\"ThrowError\") "
                + "in certain cases, but is usually not recommended unless you are writing a testing library "
                + "that throws AssertionError.")
public final class ThrowError extends BugChecker implements BugChecker.ThrowTreeMatcher {

    private static final String ERROR_NAME = Error.class.getName();

    @Override
    public Description matchThrow(ThrowTree tree, VisitorState state) {
        ExpressionTree expression = tree.getExpression();
        if (expression instanceof NewClassTree) {
            NewClassTree newClassTree = (NewClassTree) expression;
            if (ASTHelpers.isCastable(
                    ASTHelpers.getType(newClassTree.getIdentifier()),
                    state.getTypeFromString(ERROR_NAME),
                    state)
                    // Don't discourage developers from testing edge cases involving Errors.
                    // It's also fine for tests throw AssertionError internally in test objects.
                    && !TestCheckUtils.isTestCode(state)) {
                return describeMatch(tree);
            }
        }
        return Description.NO_MATCH;
    }
}
