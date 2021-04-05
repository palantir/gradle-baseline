/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Types;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ConsistentInterfaces",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "Sub-types should have variable names consistent with the super-type")
public final class ConsistentInterfaces extends BugChecker implements MethodTreeMatcher {
    private static final Pattern UNKNOWN_ARG_NAME = Pattern.compile("^args\\d+$");

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        Symbol sym = ASTHelpers.getSymbol(tree);
        if (!(sym instanceof MethodSymbol) || sym.isStatic()) {
            return Description.NO_MATCH;
        }

        MethodSymbol methodSymbol = (MethodSymbol) sym;
        return getNonParameterizedSuperMethod(methodSymbol, state.getTypes())
                .filter(ConsistentInterfaces::hasMeaningfulArgNames)
                .map(superMethod -> {
                    SuggestedFix fix = IntStream.range(0, superMethod.params.size())
                            .mapToObj(i -> {
                                String param = methodSymbol.params.get(i).name.toString();
                                String superParam =
                                        superMethod.params.get(i).name.toString();
                                if (param.equals(superParam)) {
                                    return Stream.<SuggestedFix>empty();
                                }
                                VariableTree paramTree = tree.getParameters().get(i);
                                return Stream.of(SuggestedFixes.renameVariable(paramTree, superParam, state));
                            })
                            .flatMap(Function.identity())
                            .reduce(SuggestedFix.builder(), SuggestedFix.Builder::merge, SuggestedFix.Builder::merge)
                            .build();

                    if (fix.isEmpty()) {
                        return Description.NO_MATCH;
                    }

                    return buildDescription(tree).addFix(fix).build();
                })
                .orElse(Description.NO_MATCH);
    }

    private static boolean hasMeaningfulArgNames(MethodSymbol methodSymbol) {
        return !methodSymbol.params().stream()
                .allMatch(symbol ->
                        UNKNOWN_ARG_NAME.matcher(symbol.name.toString()).matches());
    }

    /**
     * Returns the first super method of the given method that neither comes from a parameterized type or is itself
     * parameterized.
     */
    private static Optional<MethodSymbol> getNonParameterizedSuperMethod(MethodSymbol methodSymbol, Types types) {
        ClassSymbol owner = methodSymbol.enclClass();
        return types.closure(owner.type).stream()
                .filter(superType -> superType.getTypeArguments().isEmpty())
                .filter(superType -> !types.isSameType(superType, owner.type))
                .flatMap(superType -> Streams.stream(superType.tsym.members().getSymbolsByName(methodSymbol.name)))
                .filter(MethodSymbol.class::isInstance)
                .map(MethodSymbol.class::cast)
                .filter(superMethod -> superMethod.getTypeParameters().isEmpty()
                        && !superMethod.isStatic()
                        && methodSymbol.overrides(superMethod, owner, types, false))
                .findFirst();
    }
}
