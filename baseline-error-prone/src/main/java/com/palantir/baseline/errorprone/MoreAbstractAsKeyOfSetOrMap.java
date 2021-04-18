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

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.AbstractAsKeyOfSetOrMap;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.function.IntPredicate;

/** Extension atop {@link AbstractAsKeyOfSetOrMap} to apply to caches and collectors. */
abstract class MoreAbstractAsKeyOfSetOrMap extends AbstractAsKeyOfSetOrMap {

    private static final Matcher<ExpressionTree> GUAVA_CACHE_BUILDER = MethodMatchers.instanceMethod()
            .onDescendantOf("com.google.common.cache.CacheBuilder")
            .named("build");

    private static final Matcher<ExpressionTree> CAFFEINE_CACHE_BUILDER = MethodMatchers.instanceMethod()
            .onDescendantOf("com.github.benmanes.caffeine.cache.Caffeine")
            .namedAnyOf("build", "buildAsync");

    private static final Matcher<ExpressionTree> SET_COLLECTOR = MethodMatchers.staticMethod()
            .onClass("java.util.stream.Collectors")
            .named("toSet")
            .withParameters();

    private static final Matcher<ExpressionTree> UNMODIFIABLE_SET_COLLECTOR = MethodMatchers.staticMethod()
            .onClass("java.util.stream.Collectors")
            .named("toUnmodifiableSet")
            .withParameters();

    private static final Matcher<ExpressionTree> IMMUTABLE_SET_COLLECTOR = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.ImmutableSet")
            .named("toImmutableSet")
            .withParameters();

    private static final Matcher<MethodInvocationTree> MAP_COLLECTOR = Matchers.allOf(
            // We could inspect the fourth argument for hash-based maps in the future. That method
            // is relatively uncommon, and the other overloads use HashMap.
            argumentCount(count -> count < 4),
            MethodMatchers.staticMethod().onClass("java.util.stream.Collectors").named("toMap"));

    private static final Matcher<MethodInvocationTree> CONCURRENT_MAP_COLLECTOR = Matchers.allOf(
            // We could inspect the fourth argument for hash-based maps in the future. That method
            // is relatively uncommon, and the other overloads use HashMap.
            argumentCount(count -> count < 4),
            MethodMatchers.staticMethod().onClass("java.util.stream.Collectors").named("toConcurrentMap"));

    // Unlike map and concurrentMap collectors, 'toUnmodifiableMap' provides no overload to specify the map
    // implementation.
    private static final Matcher<ExpressionTree> UNMODIFIABLE_MAP_COLLECTOR =
            MethodMatchers.staticMethod().onClass("java.util.stream.Collectors").named("toUnmodifiableMap");

    private static final Matcher<ExpressionTree> IMMUTABLE_MAP_COLLECTOR = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.ImmutableMap")
            .named("toImmutableMap");

    private static final Matcher<ExpressionTree> JAVA_UTIL_MAP_OF =
            MethodMatchers.staticMethod().onClass("java.util.Map").named("of");

    private static final Matcher<ExpressionTree> JAVA_UTIL_SET_OF =
            MethodMatchers.staticMethod().onClass("java.util.Set").named("of");

    private static final Matcher<ExpressionTree> IMMUTABLE_MAP_OF = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.ImmutableMap")
            .namedAnyOf("of", "copyOf");

    private static final Matcher<ExpressionTree> IMMUTABLE_SET_OF = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.ImmutableSet")
            .namedAnyOf("of", "copyOf");

    private static final Matcher<ExpressionTree> STREAMEX_TO_MAP = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.EntryStream")
            .namedAnyOf("toMap", "toImmutableMap");

    private static final Matcher<ExpressionTree> STREAMEX_TO_SET = MethodMatchers.instanceMethod()
            .onDescendantOf("one.util.streamex.AbstractStreamEx")
            .namedAnyOf("toSet", "toImmutableSet");

    private static final Matcher<MethodInvocationTree> HASH_KEYED_METHODS = Matchers.anyOf(
            GUAVA_CACHE_BUILDER,
            CAFFEINE_CACHE_BUILDER,
            JAVA_UTIL_MAP_OF,
            JAVA_UTIL_SET_OF,
            IMMUTABLE_MAP_OF,
            IMMUTABLE_SET_OF,
            STREAMEX_TO_MAP,
            STREAMEX_TO_SET);

    private static final Matcher<MethodInvocationTree> HASH_KEYED_COLLECTOR_METHODS = Matchers.anyOf(
            SET_COLLECTOR,
            UNMODIFIABLE_SET_COLLECTOR,
            IMMUTABLE_SET_COLLECTOR,
            MAP_COLLECTOR,
            CONCURRENT_MAP_COLLECTOR,
            UNMODIFIABLE_MAP_COLLECTOR,
            IMMUTABLE_MAP_COLLECTOR);

    @Override
    public final Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        Description superResult = super.matchMethodInvocation(tree, state);
        if (!Description.NO_MATCH.equals(superResult)) {
            return superResult;
        }
        if (HASH_KEYED_METHODS.matches(tree, state)) {
            return checkType(tree, ASTHelpers.getResultType(tree), state);
        }
        if (HASH_KEYED_COLLECTOR_METHODS.matches(tree, state)) {
            Type collectorType = ASTHelpers.getResultType(tree);
            if (collectorType == null) {
                return Description.NO_MATCH;
            }
            Symbol collectorSymbol = state.getSymbolFromString("java.util.stream.Collector");
            if (collectorSymbol == null) {
                return Description.NO_MATCH;
            }
            Type asParameterizedCollector = state.getTypes().asSuper(collectorType, collectorSymbol);
            if (asParameterizedCollector == null) {
                return Description.NO_MATCH;
            }
            // Collector<T, A, R>
            // [0] T: input
            // [1] A: accumulator
            // [2] R: result type
            Type collectorResultType =
                    asParameterizedCollector.getTypeArguments().get(2);
            return checkType(tree, collectorResultType, state);
        }

        return Description.NO_MATCH;
    }

    private Description checkType(Tree tree, Type type, VisitorState state) {
        List<Type> typeArguments = type.getTypeArguments();
        if (!typeArguments.isEmpty() && isBadType(typeArguments.get(0), state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }

    private static Matcher<MethodInvocationTree> argumentCount(IntPredicate intPredicate) {
        return (methodInvocationTree, state) ->
                intPredicate.test(methodInvocationTree.getArguments().size());
    }
}
