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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ConsistentInterfaceImplementation",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "Implementations should have variable names consistent with the interface")
public final class ConsistentInterfaceImplementation extends BugChecker implements MethodTreeMatcher {

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        Symbol sym = ASTHelpers.getSymbol(tree);
        if (!(sym instanceof MethodSymbol) || sym.isStatic() || (sym.flags_field & Flags.ABSTRACT) != 0) {
            return Description.NO_MATCH;
        }

        MethodSymbol methodSymbol = (MethodSymbol) sym;
        return getSuperMethod(methodSymbol, state.getTypes())
                .flatMap(superMethod -> {
                    int paramIndex = 0;
                    for (List<String> segment : segmentByType(superMethod.params())) {
                        if (segment.size() > 1) {
                            List<String> actual = toVariableNames(
                                    methodSymbol.params().subList(paramIndex, paramIndex + segment.size()));
                            if (mismatchedParams(segment, actual)) {
                                return Optional.of(buildDescription(tree)
                                        .setMessage("Implementation of interface has parameters with equal types with"
                                                + " mismatched names")
                                        .build());
                            }
                        }
                        paramIndex += segment.size();
                    }
                    return Optional.empty();
                })
                .orElse(Description.NO_MATCH);
    }

    private boolean mismatchedParams(List<String> expected, List<String> actual) {
        if (!expected.equals(actual)) {
            for (int i = 0; i < actual.size(); i++) {
                String current = actual.get(i);
                int expectedLocation = expected.indexOf(current);
                if (expectedLocation != -1 && expectedLocation != i) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Optional<MethodSymbol> getSuperMethod(MethodSymbol methodSymbol, Types types) {
        ClassSymbol owner = methodSymbol.enclClass();
        return types.closure(owner.type).stream()
                .filter(superType -> !types.isSameType(superType, owner.type))
                .flatMap(superType -> Streams.stream(superType.tsym.members().getSymbolsByName(methodSymbol.name)))
                .filter(MethodSymbol.class::isInstance)
                .map(MethodSymbol.class::cast)
                .filter(sym -> !sym.isStatic() && methodSymbol.overrides(sym, owner, types, false))
                .findFirst();
    }

    private static List<List<String>> segmentByType(List<VarSymbol> symbols) {
        if (symbols.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableList.Builder<List<String>> segments = ImmutableList.builder();
        List<VarSymbol> currentSegment = new ArrayList<>();
        currentSegment.add(symbols.get(0));

        for (int i = 1; i < symbols.size(); i++) {
            VarSymbol current = symbols.get(i);
            if (!symbols.get(i - 1).type.equalsIgnoreMetadata(current.type)) {
                segments.add(toVariableNames(currentSegment));
                currentSegment.clear();
            }
            currentSegment.add(current);
        }
        segments.add(toVariableNames(currentSegment));

        return segments.build();
    }

    private static List<String> toVariableNames(List<VarSymbol> symbols) {
        return symbols.stream().map(sym -> sym.name.toString()).collect(Collectors.toList());
    }
}
