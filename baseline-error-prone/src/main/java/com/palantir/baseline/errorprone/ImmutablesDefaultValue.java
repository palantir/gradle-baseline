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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.AnnotationMatcherUtils;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "`default` methods in immutable interfaces need to be annotated with @Value.Default to ensure the "
                + "generated class includes them as fields. This check can be suppressed if the method is simply "
                + "an auxiliary method and not meant to be a field.",
        severity = SeverityLevel.WARNING)
public final class ImmutablesDefaultValue extends BugChecker implements BugChecker.MethodTreeMatcher {

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (!tree.getModifiers().getFlags().contains(Modifier.DEFAULT)) {
            return Description.NO_MATCH;
        }

        ClassTree enclosingClass = state.findEnclosing(ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        if (methodSymbol == null) {
            return Description.NO_MATCH;
        }

        if (ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Default", state)) {
            return Description.NO_MATCH;
        }

        if (Matchers.hasAnnotation("org.immutables.value.Value.Immutable").matches(enclosingClass, state)) {
            // Check if @Value.Style(defaultAsDefault = true) is present
            if (hasDefaultAsDefaultStyle(enclosingClass, state)) {
                return Description.NO_MATCH;
            }

            SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
            String qualifiedValueDefault =
                    SuggestedFixes.qualifyType(state, fixBuilder, "org.immutables.value.Value.Default");

            SuggestedFix fix = fixBuilder
                    .prefixWith(tree, "@" + qualifiedValueDefault + "\n")
                    .build();

            return buildDescription(tree)
                    .setMessage("`default` method '" + tree.getName() + "' in immutable interface '"
                            + enclosingClass.getSimpleName()
                            + "' needs @Value.Default to be included as a field in the generated class")
                    .addFix(fix)
                    .build();
        }

        return Description.NO_MATCH;
    }

    private static boolean hasDefaultAsDefaultStyle(ClassTree classTree, VisitorState state) {
        List<? extends AnnotationTree> annotations = classTree.getModifiers().getAnnotations();
        for (AnnotationTree annotation : annotations) {
            // Check direct @Value.Style annotation
            if (hasDefaultAsDefaultInAnnotation(annotation, state)) {
                return true;
            }

            // Check meta-annotations (annotations on the annotation)
            Symbol.ClassSymbol annotationSymbol =
                    (Symbol.ClassSymbol) ASTHelpers.getSymbol(annotation.getAnnotationType());
            if (annotationSymbol != null) {
                // Check if the annotation itself has @Value.Style with defaultAsDefault = true
                if (ASTHelpers.hasAnnotation(annotationSymbol, "org.immutables.value.Value.Style", state)) {
                    // Get the @Value.Style annotation from the meta-annotation
                    for (AnnotationMirror mirror : annotationSymbol.getAnnotationMirrors()) {
                        if ("org.immutables.value.Value.Style"
                                .equals(mirror.getAnnotationType().toString())) {
                            // Check if defaultAsDefault = true in the meta-annotation
                            for (var entry : mirror.getElementValues().entrySet()) {
                                ExecutableElement key = entry.getKey();
                                AnnotationValue value = entry.getValue();
                                if ("defaultAsDefault"
                                        .equals(key.getSimpleName().toString())) {
                                    if (Boolean.TRUE.equals(value.getValue())) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasDefaultAsDefaultInAnnotation(AnnotationTree annotation, VisitorState state) {
        Symbol.ClassSymbol annotationSymbol = (Symbol.ClassSymbol) ASTHelpers.getSymbol(annotation.getAnnotationType());
        if (annotationSymbol != null
                && "org.immutables.value.Value.Style"
                        .equals(annotationSymbol.getQualifiedName().toString())) {
            ExpressionTree defaultAsDefaultValue = AnnotationMatcherUtils.getArgument(annotation, "defaultAsDefault");
            if (defaultAsDefaultValue != null) {
                if ("true".equals(state.getSourceForNode(defaultAsDefaultValue))) {
                    return true;
                }
            }
        }
        return false;
    }
}
