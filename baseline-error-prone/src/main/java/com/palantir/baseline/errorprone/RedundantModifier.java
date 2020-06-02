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
import com.google.errorprone.BugPattern.SeverityLevel;
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
import java.util.Locale;
import javax.lang.model.element.Modifier;

/**
 * In a future change we may want to validate against unnecessary modifiers based on encapsulating component visibility,
 * for example there's no reason to allow a public constructor for a private class.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "RedundantModifier",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "Avoid using redundant modifiers")
public final class RedundantModifier extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodTreeMatcher, BugChecker.VariableTreeMatcher {

    private static final Matcher<ClassTree> STATIC_ENUM_OR_INTERFACE = Matchers.allOf(
            Matchers.anyOf(Matchers.kindIs(Tree.Kind.ENUM), Matchers.kindIs(Tree.Kind.INTERFACE)),
            MoreMatchers.hasExplicitModifier(Modifier.STATIC));

    private static final Matcher<MethodTree> PRIVATE_ENUM_CONSTRUCTOR = Matchers.allOf(
            Matchers.methodIsConstructor(),
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.ENUM)),
            MoreMatchers.hasExplicitModifier(Modifier.PRIVATE));

    private static final Matcher<MethodTree> STATIC_FINAL_METHOD = Matchers.allOf(
            MoreMatchers.hasExplicitModifier(Modifier.STATIC), MoreMatchers.hasExplicitModifier(Modifier.FINAL));

    private static final Matcher<MethodTree> UNNECESSARY_INTERFACE_METHOD_MODIFIERS = Matchers.allOf(
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.INTERFACE)),
            Matchers.not(Matchers.isStatic()),
            Matchers.not(Matchers.hasModifier(Modifier.DEFAULT)),
            Matchers.anyOf(
                    MoreMatchers.hasExplicitModifier(Modifier.PUBLIC),
                    MoreMatchers.hasExplicitModifier(Modifier.ABSTRACT)));

    private static final Matcher<MethodTree> INTERFACE_STATIC_METHOD_MODIFIERS = Matchers.allOf(
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.INTERFACE)),
            Matchers.isStatic(),
            MoreMatchers.hasExplicitModifier(Modifier.PUBLIC));

    private static final Matcher<VariableTree> INTERFACE_FIELD_MODIFIERS = Matchers.allOf(
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.INTERFACE)),
            Matchers.isStatic(),
            Matchers.anyOf(
                    MoreMatchers.hasExplicitModifier(Modifier.PUBLIC),
                    MoreMatchers.hasExplicitModifier(Modifier.STATIC),
                    MoreMatchers.hasExplicitModifier(Modifier.FINAL)));

    // Applies to both abstract class abstract methods and interface methods.
    private static final Matcher<VariableTree> ABSTRACT_METHOD_MODIFIERS = Matchers.allOf(
            Matchers.enclosingNode(
                    Matchers.allOf(Matchers.kindIs(Tree.Kind.METHOD), Matchers.hasModifier(Modifier.ABSTRACT))),
            MoreMatchers.hasExplicitModifier(Modifier.FINAL));

    private static final Matcher<ClassTree> INTERFACE_NESTED_CLASS_MODIFIERS = Matchers.allOf(
            MoreMatchers.classEnclosingClass(Matchers.kindIs(Tree.Kind.INTERFACE)),
            Matchers.anyOf(
                    MoreMatchers.hasExplicitModifier(Modifier.PUBLIC),
                    MoreMatchers.hasExplicitModifier(Modifier.STATIC)));

    private static final Matcher<MethodTree> UNNECESSARY_FINAL_METHOD_ON_FINAL_CLASS = Matchers.allOf(
            Matchers.not(Matchers.isStatic()),
            Matchers.enclosingClass(
                    Matchers.allOf(Matchers.kindIs(Tree.Kind.CLASS), MoreMatchers.hasExplicitModifier(Modifier.FINAL))),
            Matchers.allOf(
                    MoreMatchers.hasExplicitModifier(Modifier.FINAL),
                    Matchers.not(Matchers.hasAnnotation(SafeVarargs.class))));

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (INTERFACE_NESTED_CLASS_MODIFIERS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Types nested in interfaces are public and static by default.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.PUBLIC, Modifier.STATIC))
                    .build();
        }
        if (STATIC_ENUM_OR_INTERFACE.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage(tree.getKind().name().toLowerCase(Locale.ENGLISH)
                            + "s are static by default. The 'static' modifier is unnecessary.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.STATIC))
                    .build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (PRIVATE_ENUM_CONSTRUCTOR.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Enum constructors are private by default. The 'private' modifier is unnecessary.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.PRIVATE))
                    .build();
        }
        if (STATIC_FINAL_METHOD.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Static methods cannot be overridden. The 'final' modifier is unnecessary.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.FINAL))
                    .build();
        }
        if (UNNECESSARY_INTERFACE_METHOD_MODIFIERS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Interface methods are public and abstract by default. "
                            + "The 'public' and 'abstract' modifiers are unnecessary.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.PUBLIC, Modifier.ABSTRACT))
                    .build();
        }
        if (INTERFACE_STATIC_METHOD_MODIFIERS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Interface components are public by default. The 'public' modifier is unnecessary.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.PUBLIC))
                    .build();
        }
        if (UNNECESSARY_FINAL_METHOD_ON_FINAL_CLASS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Redundant 'final' modifier on an instance method of a final class.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.FINAL))
                    .build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (INTERFACE_FIELD_MODIFIERS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Interface fields are public, static, and final by default. "
                            + "These modifiers are unnecessary to specify.")
                    .addFix(SuggestedFixes.removeModifiers(
                            tree, state, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                    .build();
        }
        if (ABSTRACT_METHOD_MODIFIERS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("The final modifier has no impact on abstract methods.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.FINAL))
                    .build();
        }
        return Description.NO_MATCH;
    }
}
