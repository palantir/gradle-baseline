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
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.immutables.value.Value.Immutable;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ConsistentOverrides",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "Method overrides should have variable names consistent with the super-method when there "
                + "are multiple parameters with the same type to avoid incorrectly binding values to variables.")
public final class ConsistentOverrides extends BugChecker implements MethodTreeMatcher {
    private static final Pattern UNKNOWN_ARG_NAME = Pattern.compile("^args\\d+$");

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        Symbol sym = ASTHelpers.getSymbol(tree);
        if (!(sym instanceof MethodSymbol) || sym.isStatic()) {
            return Description.NO_MATCH;
        }

        MethodSymbol methodSymbol = (MethodSymbol) sym;
        if (methodSymbol.params().size() <= 1) {
            return Description.NO_MATCH;
        }

        List<? extends VariableTree> paramTrees = tree.getParameters();
        getNonParameterizedSuperMethod(methodSymbol, state.getTypes())
                .filter(ConsistentOverrides::hasMeaningfulArgNames)
                .ifPresent(superMethod -> {
                    Map<Type, List<ParamEntry>> superParamsByType = IntStream.range(0, paramTrees.size())
                            .mapToObj(i -> ParamEntry.of(superMethod.params().get(i), i))
                            .collect(Collectors.groupingBy(ParamEntry::type));

                    superParamsByType.values().forEach(params -> {
                        if (params.size() <= 1) {
                            return;
                        }

                        for (ParamEntry expectedParam : params) {
                            int index = expectedParam.index();
                            String param = methodSymbol.params.get(index).name.toString();
                            if (equivalentNames(param, expectedParam.name())) {
                                continue;
                            }

                            state.reportMatch(buildDescription(tree)
                                    .addFix(SuggestedFixes.renameVariable(
                                            paramTrees.get(index), expectedParam.name(), state))
                                    .build());
                        }
                    });
                });

        return Description.NO_MATCH;
    }

    private static boolean equivalentNames(String actual, String expected) {
        return actual.equals(expected) || actual.equals("_" + expected);
    }

    private static boolean hasMeaningfulArgNames(MethodSymbol methodSymbol) {
        return !methodSymbol.params().stream()
                .allMatch(symbol -> symbol.name.length() == 1
                        || UNKNOWN_ARG_NAME.matcher(symbol.name).matches());
    }

    private static Optional<MethodSymbol> getNonParameterizedSuperMethod(MethodSymbol methodSymbol, Types types) {
        return ASTHelpers.findSuperMethods(methodSymbol, types).stream()
                .filter(superMethod -> superMethod.owner.getTypeParameters().isEmpty())
                .filter(superMethod -> superMethod.getTypeParameters().isEmpty())
                .findFirst();
    }

    @Immutable
    interface ParamEntry {
        Type type();

        String name();

        int index();

        static ParamEntry of(VarSymbol symbol, int index) {
            return ImmutableParamEntry.builder()
                    .type(symbol.type)
                    .name(symbol.name.toString())
                    .index(index)
                    .build();
        }
    }
}
