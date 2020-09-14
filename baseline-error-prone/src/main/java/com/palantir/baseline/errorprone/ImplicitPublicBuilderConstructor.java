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
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ImplicitPublicBuilderConstructor",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "A Builder with a static factory method on the encapsulating class must have a private constructor. "
                + "Minimizing unnecessary public API prevents future API breaks from impacting consumers. ")
public final class ImplicitPublicBuilderConstructor extends BugChecker implements BugChecker.ClassTreeMatcher {

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        ClassSymbol enclosingClass = ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree));
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }
        if (!isValidBuilderClass(tree)) {
            return Description.NO_MATCH;
        }
        List<MethodTree> constructors = ASTHelpers.getConstructors(tree);
        if (constructors.size() != 1 || !ASTHelpers.isGeneratedConstructor(Iterables.getOnlyElement(constructors))) {
            return Description.NO_MATCH;
        }

        if (!hasStaticBuilderFactory(enclosingClass, tree, state)) {
            // No factory method, the public constructor is used
            return Description.NO_MATCH;
        }

        // If no fields exist, the constructor is placed after the curly brace
        int constructorPosition = ((JCTree) tree).getStartPosition()
                + state.getSourceForNode(tree).indexOf('{')
                + 1;

        for (Tree member : tree.getMembers()) {
            if (member.getKind() == Kind.VARIABLE) {
                int endPosition = state.getEndPosition(member);
                if (endPosition > constructorPosition) {
                    constructorPosition = endPosition;
                }
            }
        }
        return buildDescription(tree)
                .addFix(SuggestedFix.builder()
                        .replace(
                                constructorPosition,
                                constructorPosition,
                                String.format("\nprivate %s() {}", tree.getSimpleName()))
                        .build())
                .build();
    }

    private static boolean isValidBuilderClass(ClassTree tree) {
        return tree.getSimpleName().contentEquals("Builder")
                && tree.getImplementsClause().isEmpty()
                && tree.getExtendsClause() == null
                && tree.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    private static boolean hasStaticBuilderFactory(
            ClassSymbol classSymbol, ClassTree builderClassTree, VisitorState state) {
        Set<MethodSymbol> matching = ASTHelpers.findMatchingMethods(
                state.getName("builder"),
                methodSymbol -> methodSymbol != null
                        && methodSymbol.isStatic()
                        && ASTHelpers.isSameType(
                                ASTHelpers.getType(builderClassTree), methodSymbol.getReturnType(), state),
                classSymbol.type,
                state.getTypes());
        return !matching.isEmpty();
    }
}
