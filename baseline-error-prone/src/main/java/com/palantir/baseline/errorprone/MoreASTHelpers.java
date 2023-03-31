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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.Comparator;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility functionality that does not exist in {@link com.google.errorprone.util.ASTHelpers}. */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public final class MoreASTHelpers {

    /** Removes any type that is a subtype of another type in the set. */
    @SuppressWarnings("ReferenceEquality")
    static ImmutableList<Type> flattenTypesForAssignment(ImmutableList<Type> input, VisitorState state) {
        ImmutableList<Type> types =
                input.stream().map(type -> broadenAnonymousType(type, state)).collect(ImmutableList.toImmutableList());
        ImmutableList.Builder<Type> deduplicatedBuilder = ImmutableList.builderWithExpectedSize(types.size());
        for (int i = 0; i < types.size(); i++) {
            Type current = types.get(i);
            boolean duplicate = false;
            for (int j = 0; j < i; j++) {
                if (ASTHelpers.isSameType(types.get(j), current, state)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduplicatedBuilder.add(current);
            }
        }
        ImmutableList<Type> deduplicated = deduplicatedBuilder.build();
        return deduplicated.stream()
                .filter(type -> deduplicated.stream()
                        .noneMatch(item ->
                                // An item cannot deduplicate itself
                                type != item && state.getTypes().isSubtype(type, item)))
                // Sort by pretty name
                .sorted(Comparator.comparing(type -> SuggestedFixes.prettyType(state, null, type)))
                .collect(ImmutableList.toImmutableList());
    }

    /** Anonymous types cannot be referenced directly, so we must use the supertype. */
    private static Type broadenAnonymousType(Type input, VisitorState state) {
        Type upperBound = input.getUpperBound();
        if (upperBound != null) {
            return broadenAnonymousType(upperBound, state);
        }
        if (input.tsym.isAnonymous()) {
            return broadenAnonymousType(state.getTypes().supertype(input), state);
        }
        return input;
    }

    /** Gets thrown exceptions from a {@link TryTree} excluding those thrown from {@link CatchTree}. */
    static ImmutableSet<Type> getThrownExceptionsFromTryBody(TryTree tree, VisitorState state) {
        ImmutableSet.Builder<Type> results = ImmutableSet.builder();
        results.addAll(ASTHelpers.getThrownExceptions(tree.getBlock(), state));
        tree.getResources().forEach(resource -> {
            results.addAll(ASTHelpers.getThrownExceptions(resource, state));
            Type resourceType = ASTHelpers.getType(resource);
            if (resourceType != null && resourceType.tsym instanceof Symbol.ClassSymbol) {
                MoreASTHelpers.getCloseMethod((Symbol.ClassSymbol) resourceType.tsym, state)
                        .map(Symbol.MethodSymbol::getThrownTypes)
                        .ifPresent(results::addAll);
            }
        });
        return results.build();
    }

    /** Returns an optional of the {@link AutoCloseable#close()} method on the provided symbol. */
    private static Optional<Symbol.MethodSymbol> getCloseMethod(Symbol.ClassSymbol symbol, VisitorState state) {
        Types types = state.getTypes();
        return symbol.getEnclosedElements().stream()
                .filter(sym -> types.isAssignable(symbol.type, state.getTypeFromString(AutoCloseable.class.getName()))
                        && sym.getSimpleName().contentEquals("close")
                        && sym.getTypeParameters().isEmpty())
                .map(e -> (Symbol.MethodSymbol) e)
                .findFirst();
    }

    /** Returns either the input type, or the types that make up the union in the case of a union type. */
    static ImmutableList<Type> expandUnion(@Nullable Type type) {
        if (type == null) {
            return ImmutableList.of();
        }
        if (type.isUnion()) {
            Type.UnionClassType unionType = (Type.UnionClassType) type;
            return ImmutableList.copyOf(unionType.getAlternativeTypes());
        }
        return ImmutableList.of(type);
    }

    public static Type getResultType(Tree tree) {
        return tree instanceof ExpressionTree
                ? ASTHelpers.getResultType((ExpressionTree) tree)
                : ASTHelpers.getType(tree);
    }

    private MoreASTHelpers() {}
}
