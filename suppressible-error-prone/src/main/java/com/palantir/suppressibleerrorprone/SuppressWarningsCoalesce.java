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

package com.palantir.suppressibleerrorprone;

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
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Name;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "blah")
public final class SuppressWarningsCoalesce extends BugChecker
        implements BugChecker.MethodTreeMatcher, BugChecker.VariableTreeMatcher, BugChecker.ClassTreeMatcher {

    private static final MultiMatcher<Tree, AnnotationTree> HAS_REPEATABLE_SUPPRESSION = Matchers.annotations(
            MatchType.AT_LEAST_ONE, Matchers.isType("com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings"));

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        return coalesceSuppressWarnings(state, tree, tree.getModifiers());
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        return coalesceSuppressWarnings(state, tree, tree.getModifiers());
    }

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        return coalesceSuppressWarnings(state, tree, tree.getModifiers());
    }

    private Description coalesceSuppressWarnings(VisitorState state, Tree tree, ModifiersTree modifiersTree) {
        if (!HAS_REPEATABLE_SUPPRESSION.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends AnnotationTree> suppressWarnings = modifiersTree.getAnnotations().stream()
                .filter(annotation -> {
                    Name annotationName = annotationName(annotation.getAnnotationType());
                    return annotationName.contentEquals("SuppressWarnings")
                            || annotationName.contentEquals("RepeatableSuppressWarnings");
                })
                .collect(Collectors.toList());

        if (suppressWarnings.isEmpty()) {
            return Description.NO_MATCH;
        }

        List<String> warningsToSuppress = suppressWarnings.stream()
                .sorted(suppressWarningsBeforeRepeatableSuppressedWarnings())
                .flatMap(SuppressWarningsCoalesce::annotationStringValues)
                .collect(Collectors.toList());

        if (warningsToSuppress.isEmpty()) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        suppressWarnings.forEach(fixBuilder::delete);

        String suppressWarningsString = '"' + String.join("\", \"", warningsToSuppress) + '"';

        if (warningsToSuppress.size() > 1) {
            suppressWarningsString = "{" + suppressWarningsString + "}";
        }

        fixBuilder.prefixWith(tree, "@SuppressWarnings(" + suppressWarningsString + ")");

        return buildDescription(tree)
                .setMessage("Coalescing @SuppressWarnings annotations")
                .addFix(fixBuilder.build())
                .build();
    }

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

    private static Stream<String> annotationStringValues(AnnotationTree annotation) {
        return annotation.getArguments().stream().flatMap(arg -> {
            if (arg instanceof AssignmentTree) {
                AssignmentTree assignment = (AssignmentTree) arg;
                ExpressionTree expression = assignment.getExpression();

                if (expression instanceof LiteralTree) {
                    return Stream.of((String) ((LiteralTree) expression).getValue());
                }

                if (expression instanceof NewArrayTree) {
                    NewArrayTree newArray = (NewArrayTree) expression;
                    return newArray.getInitializers().stream()
                            .map(LiteralTree.class::cast)
                            .map(LiteralTree::getValue)
                            .map(String.class::cast);
                }

                throw new UnsupportedOperationException("Unsupported assignment expression: "
                        + expression.getClass().getCanonicalName());
            }

            return Stream.empty();
        });
    }

    private static Comparator<? super AnnotationTree> suppressWarningsBeforeRepeatableSuppressedWarnings() {
        return Comparator.comparing(annotationTree ->
                annotationName(annotationTree.getAnnotationType()).contentEquals("SuppressWarnings") ? 0 : 1);
    }
}
