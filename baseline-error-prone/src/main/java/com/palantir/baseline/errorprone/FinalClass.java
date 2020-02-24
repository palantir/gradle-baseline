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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.List;
import java.util.Optional;
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
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "A class should be declared final if all of its constructors are private. Utility classes -- "
                + "i.e., classes all of whose methods and fields are static -- have a private, empty, "
                + "zero-argument constructor.\n"
                + "https://github.com/palantir/gradle-baseline/tree/develop/docs/best-practices/"
                + "java-coding-guidelines#private-constructors")
public final class FinalClass extends BugChecker implements BugChecker.ClassTreeMatcher {

    private static final Matcher<MethodTree> SIMPLIFIABLE_INSTANCE_METHOD = Matchers.allOf(
            Matchers.hasModifier(Modifier.FINAL),
            // 'static final' is redundant, however it's outside the scope of this check to fix.
            Matchers.not(Matchers.hasModifier(Modifier.STATIC)),
            Matchers.not(Matchers.hasAnnotation(SafeVarargs.class)));

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind() != Tree.Kind.CLASS) {
            // Don't apply to out interfaces and enums
            return Description.NO_MATCH;
        }
        Set<Modifier> classModifiers = tree.getModifiers().getFlags();
        if (classModifiers.contains(Modifier.FINAL) || classModifiers.contains(Modifier.ABSTRACT)) {
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
        if (isClassExtendedInternally(tree, state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree).addFix(buildFix(tree, state)).build();
    }

    private static Optional<SuggestedFix> buildFix(ClassTree tree, VisitorState state) {
        return SuggestedFixes.addModifiers(tree, state, Modifier.FINAL).map(fix -> {
            // Remove redundant 'final' methods modifers that are no longer necessary.
            SuggestedFix.Builder builder = SuggestedFix.builder().merge(fix);
            tree.getMembers().stream()
                    .filter(member -> member instanceof MethodTree)
                    .map(MethodTree.class::cast)
                    .filter(methodTree -> SIMPLIFIABLE_INSTANCE_METHOD.matches(methodTree, state))
                    .forEach(methodTree -> SuggestedFixes.removeModifiers(methodTree, state, Modifier.FINAL)
                            .ifPresent(builder::merge));
            return builder.build();
        });
    }

    private static boolean isClassExtendedInternally(ClassTree tree, VisitorState state) {
        // Encapsulated classes can be extended by other nested classes, even if they have private constructors.
        // In these cases we mustn't fail validation or suggest a final modifier.
        for (Tree typeDeclaration : state.getPath().getCompilationUnit().getTypeDecls()) {
            Boolean maybeResult = typeDeclaration.accept(
                    new TreeScanner<Boolean, Void>() {

                        @Override
                        public Boolean reduce(Boolean lhs, Boolean rhs) {
                            // Fail if any class extends 'tree'
                            return Boolean.TRUE.equals(lhs) || Boolean.TRUE.equals(rhs);
                        }

                        @Override
                        public Boolean visitClass(ClassTree classTree, Void attachment) {
                            Tree extendsClause = classTree.getExtendsClause();
                            if (extendsClause != null
                                    && ASTHelpers.isSameType(
                                            ASTHelpers.getType(tree), ASTHelpers.getType(extendsClause), state)) {
                                return true;
                            }
                            return super.visitClass(classTree, attachment);
                        }

                        @Override
                        public Boolean visitNewClass(NewClassTree newClassTree, Void attachment) {
                            if (newClassTree.getClassBody() != null
                                    && ASTHelpers.isSameType(
                                            ASTHelpers.getType(tree),
                                            ASTHelpers.getType(newClassTree.getIdentifier()),
                                            state)) {
                                return true;
                            }
                            return super.visitNewClass(newClassTree, attachment);
                        }
                    },
                    null);
            // Unfortunately TreeScanner doesn't provide a way to set a default value, so we must account for null.
            if (Boolean.TRUE.equals(maybeResult)) {
                return true;
            }
        }
        return false;
    }
}
