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
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.List;
import java.util.Optional;
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

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        List<? extends VariableTree> methodParameters = tree.getParameters();
        if (methodSymbol == null
                || methodSymbol.isStatic()
                || methodSymbol.isPrivate()
                || methodSymbol.params().size() <= 1
                || methodParameters.size() <= 1) {
            return Description.NO_MATCH;
        }

        getNonParameterizedSuperMethod(methodSymbol, state.getTypes())
                .filter(ConsistentOverrides::retainedParameterNames)
                .ifPresent(superMethod -> IntStream.range(0, methodParameters.size())
                        .mapToObj(i -> ParamEntry.of(superMethod.params().get(i), i))
                        .collect(Collectors.groupingBy(ParamEntry::type))
                        .values()
                        .forEach(params -> {
                            if (params.size() <= 1) {
                                return;
                            }

                            for (ParamEntry expectedParam : params) {
                                int index = expectedParam.index();
                                // Use the parameter VariableTree name which always retains name information
                                VariableTree parameter = methodParameters.get(index);
                                String parameterName = parameter.getName().toString();
                                String expectedParameterName = expectedParam.name();
                                if (!isMeaninglessParameterName(expectedParameterName)
                                        && !equivalentNames(parameterName, expectedParameterName)) {
                                    state.reportMatch(buildDescription(tree)
                                            .addFix(SuggestedFixes.renameVariable(
                                                    methodParameters.get(index),
                                                    retainUnderscore(parameterName, expectedParameterName),
                                                    state))
                                            .build());
                                }
                            }
                        }));

        return Description.NO_MATCH;
    }

    // Match the original names underscore prefix to appease StrictUnusedVariable
    private static String retainUnderscore(String originalName, String newName) {
        boolean originalUnderscore = originalName.startsWith("_");
        boolean newUnderscore = newName.startsWith("_");
        if (originalUnderscore == newUnderscore) {
            return newName;
        }
        if (!originalUnderscore) {
            return newName.substring(1);
        } else {
            return "_" + newName;
        }
    }

    private static boolean equivalentNames(String actual, String expected) {
        return actual.equals(expected)
                // Handle StrictUnusedVariable underscore prefixes in both directions
                || (actual.charAt(0) == '_' && actual.length() == expected.length() + 1 && actual.endsWith(expected))
                || (expected.charAt(0) == '_' && expected.length() == actual.length() + 1 && expected.endsWith(actual));
    }

    // If any parameters have names that don't match 'arg\d+', names are retained.
    private static boolean retainedParameterNames(MethodSymbol methodSymbol) {
        for (VarSymbol parameter : methodSymbol.params()) {
            if (!isUnknownParameterName(parameter.name)) {
                return true;
            }
        }
        return methodSymbol.params().isEmpty();
    }

    private static boolean isMeaninglessParameterName(CharSequence input) {
        // Single character names are unhelpful for readers, and should be overridden in implementations
        return input.length() <= 1
                // Implementation may not retain parameter names, we shouldn't create churn in this case.
                || isUnknownParameterName(input);
    }

    // Returns true if code was compiled without javac '-parameters'
    private static boolean isUnknownParameterName(CharSequence input) {
        int length = input.length();
        if (length > 3 && input.charAt(0) == 'a' && input.charAt(1) == 'r' && input.charAt(2) == 'g') {
            for (int i = 3; i < length; i++) {
                char current = input.charAt(i);
                if (!Character.isDigit(current)) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
