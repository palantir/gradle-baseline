/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.MultiMatcher;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Name;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "blah")
public final class SuppressWarningsCoalesce extends BugChecker implements BugChecker.MethodTreeMatcher {

    private static final MultiMatcher<Tree, AnnotationTree> HAS_REPEATABLE_SUPPRESSION = Matchers.annotations(
            MatchType.ALL, Matchers.isType("com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings"));

    private static Name annotationName(Tree annotationType) {
        if (annotationType instanceof IdentifierTree) {
            return ((IdentifierTree) annotationType).getName();
        }

        if (annotationType instanceof MemberSelectTree) {
            return ((MemberSelectTree) annotationType).getIdentifier();
        }

        throw new UnsupportedOperationException(
                "Unsupported annotation type: " + annotationType.getClass().getCanonicalName());
    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (!HAS_REPEATABLE_SUPPRESSION.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends AnnotationTree> suppressWarnings = tree.getModifiers().getAnnotations().stream()
                .filter(annotation -> {
                    Tree annotationType = annotation.getAnnotationType();
                    Name annotationName = annotationName(annotationType);
                    if (annotationName.contentEquals("SuppressWarnings")
                            || annotationName.contentEquals("RepeatableSuppressWarnings")) {
                        return true;
                    }

                    if (annotationType instanceof IdentifierTree) {
                        return ((IdentifierTree) annotationType).getName().contentEquals("SuppressWarnings");
                    }

                    if (annotationType instanceof MemberSelectTree) {
                        return ((MemberSelectTree) annotationType)
                                .getIdentifier()
                                .contentEquals("RepeatableSuppressWarnings");
                    }

                    return false;
                })
                .collect(Collectors.toList());

        if (suppressWarnings.isEmpty()) {
            return Description.NO_MATCH;
        }

        String warningsToSuppress = suppressWarnings.stream()
                .flatMap(annotation -> {
                    return annotation.getArguments().stream().flatMap(arg -> {
                        if (arg instanceof AssignmentTree) {
                            AssignmentTree assignment = (AssignmentTree) arg;
                            return Stream.of((String) ((LiteralTree) assignment.getExpression()).getValue());
                        }

                        return Stream.empty();
                    });
                })
                .collect(Collectors.joining("\",\""));

        if (warningsToSuppress.isEmpty()) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        suppressWarnings.forEach(fixBuilder::delete);

        fixBuilder.prefixWith(tree, "@SuppressWarnings({\"" + warningsToSuppress + "\"})");

        return buildDescription(tree)
                .setMessage("blah")
                .addFix(fixBuilder.build())
                .build();
    }
}
