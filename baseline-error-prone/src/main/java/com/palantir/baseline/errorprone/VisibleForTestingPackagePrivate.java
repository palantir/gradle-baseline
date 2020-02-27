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
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import javax.lang.model.element.Modifier;

/** Development Practices: Writing good unit tests. */
@AutoService(BugChecker.class)
@BugPattern(
        name = "VisibleForTestingPackagePrivate",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "@VisibleForTesting members should be package-private.")
public final class VisibleForTestingPackagePrivate extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodTreeMatcher, BugChecker.VariableTreeMatcher {

    private static final Matcher<Tree> matcher = Matchers.allOf(
            Matchers.hasAnnotation(VisibleForTesting.class),
            Matchers.anyOf(
                    MoreMatchers.hasExplicitModifier(Modifier.PROTECTED),
                    MoreMatchers.hasExplicitModifier(Modifier.PUBLIC)));

    private Description match(Tree tree, VisitorState state) {
        if (matcher.matches(tree, state)) {
            return buildDescription(tree)
                    // This may break code that references the visible component, so it should not
                    // be applied by default.
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.PROTECTED, Modifier.PUBLIC))
                    .build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        return match(tree, state);
    }

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        return match(tree, state);
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        return match(tree, state);
    }
}
