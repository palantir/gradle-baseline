/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import java.util.List;
import javax.lang.model.element.ElementKind;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "Concrete BugChecker implementations should be annotated "
                + "`@AutoService(BugChecker.class)` for auto registration with error-prone.",
        severity = SeverityLevel.ERROR,
        tags = StandardTags.LIKELY_ERROR)
public final class BugCheckerAutoService extends SuppressibleBugChecker implements ClassTreeMatcher {

    private static final String AUTO_SERVICE = "com.google.auto.service.AutoService";
    private static final Matcher<ClassTree> isBugChecker =
            Matchers.allOf(Matchers.isSubtypeOf(BugChecker.class), Matchers.hasAnnotation(BugPattern.class));

    private static final Matcher<AnnotationTree> autoServiceBugChecker = Matchers.allOf(
            Matchers.isType(AUTO_SERVICE),
            Matchers.hasArgumentWithValue("value", Matchers.classLiteral(Matchers.isSameType(BugChecker.class))));

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        if (!isBugChecker.matches(classTree, state)) {
            return Description.NO_MATCH;
        }

        TypeSymbol thisClassSymbol = ASTHelpers.getSymbol(classTree);
        if (thisClassSymbol.getKind() != ElementKind.CLASS) {
            return Description.NO_MATCH;
        }

        List<? extends AnnotationTree> annotations = ASTHelpers.getAnnotations(classTree);
        boolean hasAutoServiceBugChecker =
                annotations.stream().anyMatch(annotationTree -> autoServiceBugChecker.matches(annotationTree, state));
        if (hasAutoServiceBugChecker) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fix = SuggestedFix.builder();
        String autoService = SuggestedFixes.qualifyType(state, fix, AUTO_SERVICE);
        String bugChecker = SuggestedFixes.qualifyType(state, fix, BugChecker.class.getName());
        return buildDescription(classTree)
                .addFix(fix.prefixWith(classTree, "@" + autoService + "(" + bugChecker + ".class)\n")
                        .build())
                .build();
    }
}
