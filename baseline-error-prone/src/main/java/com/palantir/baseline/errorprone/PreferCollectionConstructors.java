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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferCollectionConstructors",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Since Java 7 the standard collection constructors should be used instead of the static factory "
                + "methods provided by Guava.")
public final class PreferCollectionConstructors extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newArrayList")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newArrayList")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST_WITH_CAPACITY =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .namedAnyOf("newArrayListWithCapacity", "newArrayListWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_LINKED_LIST =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newLinkedList")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_LIST_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newLinkedList")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_COW_ARRAY_LIST =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newCopyOnWriteArrayList")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_COW_ARRAY_LIST_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newCopyOnWriteArrayList")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_CONCURRENT_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newConcurrentMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newHashMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_MAP_WITH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newHashMap")
                    .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_HASH_MAP_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newHashMapWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_TREE_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newTreeMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_TREE_MAP_WITH_COMPARATOR =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newTreeMap")
                    .withParameters("java.util.Comparator");

    private static final Matcher<ExpressionTree> NEW_TREE_MAP_WITH_SORTED_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newTreeMap")
                    .withParameters("java.util.SortedMap");

    private static final Matcher<ExpressionTree> NEW_COW_ARRAY_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newCopyOnWriteArraySet")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_COW_ARRAY_SET_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newCopyOnWriteArraySet")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newLinkedHashSet")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newLinkedHashSet")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newLinkedHashSetWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_TREE_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newTreeSet")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_TREE_SET_WITH_COMPARATOR =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newTreeSet")
                    .withParameters("java.util.Comparator");

    private static final Matcher<ExpressionTree> NEW_TREE_SET_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newTreeSet")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_HASH_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newHashSet")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_SET_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newHashSet")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_HASH_SET_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newHashSetWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newLinkedHashMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP_WITH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newLinkedHashMap")
                    .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newLinkedHashMapWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newEnumMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP_WITH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newEnumMap")
                    .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP_WITH_CLASS =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newEnumMap")
                    .withParameters("java.lang.Class");

    private static final Matcher<ExpressionTree> NEW_IDENTITY_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newIdentityHashMap");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Argument type used to differentiate between an Iterable and a Collection
        List<JCExpression> args = ((JCMethodInvocation) tree).args;
        Type firstArgType = args.isEmpty() ? null : args.get(0).type;

        // For convenience
        ExpressionTree firstArg = Iterables.getFirst(tree.getArguments(), null);

        if (NEW_ARRAY_LIST.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, ArrayList.class, firstArg);
        }
        if (NEW_ARRAY_LIST_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, ArrayList.class, firstArg);
        }
        if (NEW_ARRAY_LIST_WITH_CAPACITY.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, ArrayList.class, firstArg);
        }
        if (NEW_LINKED_LIST.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedList.class, firstArg);
        }
        if (NEW_LINKED_LIST_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, LinkedList.class, firstArg);
        }
        if (NEW_COW_ARRAY_LIST.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, CopyOnWriteArrayList.class, firstArg);
        }
        if (NEW_COW_ARRAY_LIST_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, CopyOnWriteArrayList.class, firstArg);
        }
        if (NEW_CONCURRENT_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, ConcurrentHashMap.class, firstArg);
        }
        if (NEW_HASH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, HashMap.class, firstArg);
        }
        if (NEW_HASH_MAP_WITH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, HashMap.class, firstArg);
        }
        if (NEW_HASH_MAP_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, HashMap.class, firstArg);
        }
        if (NEW_COW_ARRAY_SET.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, CopyOnWriteArraySet.class, firstArg);
        }
        if (NEW_COW_ARRAY_SET_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, CopyOnWriteArraySet.class, firstArg);
        }
        if (NEW_LINKED_HASH_SET.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashSet.class, firstArg);
        }
        if (NEW_LINKED_HASH_SET_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashSet.class, firstArg);
        }
        if (NEW_LINKED_HASH_SET_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashSet.class, firstArg);
        }
        if (NEW_TREE_SET.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, TreeSet.class, firstArg);
        }
        if (NEW_TREE_SET_WITH_COMPARATOR.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, TreeSet.class, firstArg);
        }
        if (NEW_TREE_SET_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, TreeSet.class, firstArg);
        }
        if (NEW_HASH_SET.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, HashSet.class, firstArg);
        }
        if (NEW_HASH_SET_WITH_ITERABLE.matches(tree, state) && isCollectionArg(state, firstArgType)) {
            return buildConstructorFixSuggestion(tree, state, HashSet.class, firstArg);
        }
        if (NEW_HASH_SET_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, HashSet.class, firstArg);
        }
        if (NEW_TREE_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, TreeMap.class, firstArg);
        }
        if (NEW_TREE_MAP_WITH_SORTED_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, TreeMap.class, firstArg);
        }
        if (NEW_TREE_MAP_WITH_COMPARATOR.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, TreeMap.class, firstArg);
        }
        if (NEW_LINKED_HASH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashMap.class, firstArg);
        }
        if (NEW_LINKED_HASH_MAP_WITH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashMap.class, firstArg);
        }
        if (NEW_LINKED_HASH_MAP_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, LinkedHashMap.class, firstArg);
        }
        if (NEW_ENUM_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, EnumMap.class, firstArg);
        }
        if (NEW_ENUM_MAP_WITH_CLASS.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, EnumMap.class, firstArg);
        }
        if (NEW_ENUM_MAP_WITH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, EnumMap.class, firstArg);
        }
        if (NEW_IDENTITY_HASH_MAP.matches(tree, state)) {
            return buildConstructorFixSuggestion(tree, state, IdentityHashMap.class, firstArg);
        }
        return Description.NO_MATCH;
    }

    private Description buildConstructorFixSuggestion(
            MethodInvocationTree tree,
            VisitorState state,
            Class<?> collectionClass,
            ExpressionTree argTree) {
        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        String collectionType = SuggestedFixes.qualifyType(state, fixBuilder, collectionClass.getName());
        String typeArgs = tree.getTypeArguments()
                .stream()
                .map(state::getSourceForNode)
                .collect(Collectors.joining(", "));
        String arg = argTree == null ? "" : state.getSourceForNode(argTree);
        String replacement = "new " + collectionType + "<" + typeArgs + ">(" + arg + ")";
        return buildDescription(tree)
                .setMessage("The factory method call should be replaced with a constructor call. " +
                        "See https://git.io/JeCT6 for more information.")
                .addFix(fixBuilder.replace(tree, replacement).build())
                .build();
    }

    private boolean isCollectionArg(VisitorState state, Type type) {
        Types types = state.getTypes();
        return types.isSubtype(types.erasure(type), types.erasure(state.getTypeFromString("java.util.Collection")));
    }
}
