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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "FinalClass",
        // Support legacy suppressions from checkstyle
        altNames = {"checkstyle:finalclass", "checkstyle:FinalClass"},
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "A class should be declared final if all of its constructors are private. Utility classes -- "
                + "i.e., classes all of whose methods and fields are static -- have a private, empty, "
                + "zero-argument constructor.\n"
                + "https://github.com/palantir/gradle-baseline/tree/develop/docs/best-practices/"
                + "java-coding-guidelines#private-constructors")
public final class FinalClass extends BugChecker implements BugChecker.ClassTreeMatcher {
    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind() != Tree.Kind.CLASS) {
            // Don't apply to out interfaces and enums
            return Description.NO_MATCH;
        }
        Set<Modifier> classModifiers = tree.getModifiers().getFlags();
        if (classModifiers.contains(Modifier.FINAL)) {
            // Already final, nothing to check
            return Description.NO_MATCH;
        }
        List<MethodTree> constructors = ASTHelpers.getConstructors(tree);
        if (constructors.isEmpty()) {
            return Description.NO_MATCH;
        }
        for (MethodTree constructor : constructors) {
            if (!constructor.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
                return Description.NO_MATCH;
            }
        }
        return buildDescription(tree)
                .addFix(SuggestedFixes.addModifiers(tree, state, Modifier.FINAL))
                .build();
    }
}
