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
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "BuilderPrivateConstructor",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "A Builder with a static factory method on the encapsulating class must have a private constructor. "
                + "Minimizing unnecessary public API prevents future API breaks from impacting consumers. ")
public final class BuilderPrivateConstructor extends BugChecker implements BugChecker.ClassTreeMatcher {

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        ClassSymbol enclosingClass = ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree));
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }
        if (!tree.getSimpleName().contentEquals("Builder")
                || !tree.getModifiers().getFlags().contains(Modifier.STATIC)) {
            return Description.NO_MATCH;
        }
        if (!tree.getImplementsClause().isEmpty() || tree.getExtendsClause() != null) {
            // Builder implements or extends another component, no need to
            return Description.NO_MATCH;
        }
        List<MethodTree> constructors = ASTHelpers.getConstructors(tree);
        if (constructors.size() != 1 || !ASTHelpers.isGeneratedConstructor(Iterables.getOnlyElement(constructors))) {
            return Description.NO_MATCH;
        }

        if (!hasStaticBuilderFactory(enclosingClass, state)) {
            // No factory method, the public constructor is used
            return Description.NO_MATCH;
        }

        // Ideally the constructor would be placed after the last field declaration.
        int openingCurlyIndex = ((JCTree) tree).getStartPosition()
                + state.getSourceForNode(tree).indexOf('{');
        return buildDescription(tree)
                .addFix(SuggestedFix.builder()
                        .replace(
                                openingCurlyIndex + 1,
                                openingCurlyIndex + 1,
                                String.format("\nprivate %s() {}", tree.getSimpleName()))
                        .build())
                .build();
    }

    private static boolean hasStaticBuilderFactory(ClassSymbol classSymbol, VisitorState state) {
        Set<MethodSymbol> matching = ASTHelpers.findMatchingMethods(
                state.getName("builder"),
                methodSymbol -> methodSymbol != null
                        && methodSymbol.getReturnType().tsym.getSimpleName().contentEquals("Builder")
                        && methodSymbol.isStatic(),
                classSymbol.type,
                state.getTypes());
        return !matching.isEmpty();
    }
}
