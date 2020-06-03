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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Suppliers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ExtendsErrorOrThrowable",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Class should not extend Error or Throwable directly. While allowed by java "
                + "it can lead to surprising behaviour if users end up catching Error or Throwable.")
public final class ExtendsErrorOrThrowable extends BugChecker implements BugChecker.ClassTreeMatcher {
    private static final Matcher<ClassTree> IS_ERROR_SUBCLASS = Matchers.isSubtypeOf(Error.class);
    private static final Matcher<Tree> IS_THROWABLE = Matchers.isSameType(Throwable.class);
    private static final Matcher<Tree> IS_ERROR_OR_THROWABLE =
            Matchers.anyOf(Matchers.isSameType(Error.class), Matchers.isSameType(Throwable.class));

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind() != Tree.Kind.CLASS) {
            // Don't apply to out interfaces and enums
            return Description.NO_MATCH;
        }

        // Match only cases where we extend from any Error type or directly from Throwable
        if (!(IS_ERROR_SUBCLASS.matches(tree, state) || IS_THROWABLE.matches(tree.getExtendsClause(), state))) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).addFix(buildFix(tree, state)).build();
    }

    private static Optional<SuggestedFix> buildFix(ClassTree tree, VisitorState state) {
        if (IS_ERROR_OR_THROWABLE.matches(tree.getExtendsClause(), state)) {
            Type exceptionType = Suppliers.typeFromClass(RuntimeException.class).get(state);
            String prettyExceptionType = SuggestedFixes.prettyType(exceptionType, state);
            return Optional.of(SuggestedFix.replace(tree.getExtendsClause(), prettyExceptionType));
        } else {
            return Optional.empty();
        }
    }
}
