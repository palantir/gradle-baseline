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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.Locale;
import javax.lang.model.element.Modifier;

/**
 * In a future change we may want to validate against unnecessary modifiers based on encapsulating
 * component visibility, for example there's no reason to allow a public constructor for a private
 * class.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "RedundantModifier",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Avoid using redundant modifiers")
public final class RedundantModifier extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodTreeMatcher {

    private static final Matcher<ClassTree> STATIC_ENUM_OR_INTERFACE = Matchers.allOf(
            Matchers.anyOf(Matchers.kindIs(Tree.Kind.ENUM), Matchers.kindIs(Tree.Kind.INTERFACE)),
            classHasExplicitModifier(Modifier.STATIC));

    private static final Matcher<MethodTree> PRIVATE_ENUM_CONSTRUCTOR = Matchers.allOf(
            Matchers.methodIsConstructor(),
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.ENUM)),
            methodHasExplicitModifier(Modifier.PRIVATE));

    private static final Matcher<MethodTree> STATIC_FINAL_METHOD = Matchers.allOf(
            methodHasExplicitModifier(Modifier.STATIC),
            methodHasExplicitModifier(Modifier.FINAL));

    private static final Matcher<MethodTree> UNNECESSARY_INTERFACE_METHOD_MODIFIERS = Matchers.allOf(
            Matchers.enclosingClass(Matchers.kindIs(Tree.Kind.INTERFACE)),
            Matchers.not(Matchers.isStatic()),
            Matchers.not(Matchers.hasModifier(Modifier.DEFAULT)),
            Matchers.anyOf(methodHasExplicitModifier(Modifier.PUBLIC), methodHasExplicitModifier(Modifier.ABSTRACT)));

    private static final Matcher<MethodTree> UNNECESSARY_FINAL_METHOD_ON_FINAL_CLASS = Matchers.allOf(
            Matchers.not(Matchers.isStatic()),
            Matchers.enclosingClass(Matchers.allOf(
                    Matchers.kindIs(Tree.Kind.CLASS),
                    classHasExplicitModifier(Modifier.FINAL))),
            Matchers.allOf(
                    methodHasExplicitModifier(Modifier.FINAL),
                    Matchers.not(Matchers.hasAnnotation(SafeVarargs.class))));

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
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
        if (UNNECESSARY_FINAL_METHOD_ON_FINAL_CLASS.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Redundant 'final' modifier on an instance method of a final class.")
                    .addFix(SuggestedFixes.removeModifiers(tree, state, Modifier.FINAL))
                    .build();
        }
        return Description.NO_MATCH;
    }

    private static Matcher<MethodTree> methodHasExplicitModifier(Modifier modifier) {
        return (Matcher<MethodTree>) (methodTree, state) ->
                methodTree.getModifiers().getFlags().contains(modifier);
    }

    private static Matcher<ClassTree> classHasExplicitModifier(Modifier modifier) {
        return (Matcher<ClassTree>) (classTree, state) ->
                classTree.getModifiers().getFlags().contains(modifier);
    }
}
