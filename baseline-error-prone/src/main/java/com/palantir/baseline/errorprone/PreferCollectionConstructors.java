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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
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
                    .named("newArrayList");

    private static final Matcher<ExpressionTree> NEW_LINKED_LIST =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newLinkedList");

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_LIST =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newCopyOnWriteArrayList");

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST_WITH_CAPACITY =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .namedAnyOf("newArrayListWithCapacity", "newArrayListWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_CONCURRENT_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newConcurrentMap")
                    .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newHashMap");

    private static final Matcher<ExpressionTree> NEW_HASH_MAP_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newHashMapWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_TREE_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newTreeMap");

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newCopyOnWriteArraySet");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newLinkedHashSet");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newLinkedHashSetWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_TREE_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newTreeSet");

    private static final Matcher<ExpressionTree> NEW_HASH_SET =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newHashSet");

    private static final Matcher<ExpressionTree> NEW_HASH_SET_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newHashSetWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newLinkedHashMap");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP_WITH_EXPECTED_SIZE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newLinkedHashMapWithExpectedSize")
                    .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newEnumMap");

    private static final Matcher<ExpressionTree> NEW_IDENTITY_HASH_MAP =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Maps")
                    .named("newIdentityHashMap");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Param types used to figure out which overload is called
        List<Type> paramTypes = ((JCTree.JCExpression) tree.getMethodSelect()).type.asMethodType().argtypes;

        // Argument types used to differentiate between an Iterable and a Collection
        List<Type> argTypes = ((JCTree.JCMethodInvocation) tree).args
                .stream().map((JCTree.JCExpression e) -> e.type).collect(Collectors.toList());

        // For convenience
        ExpressionTree arg = tree.getArguments().size() == 1 ? tree.getArguments().get(0) : null;

        if (NEW_ARRAY_LIST.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, ArrayList.class, arg);
        }
        if (NEW_ARRAY_LIST_WITH_CAPACITY.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, ArrayList.class, arg);
        }
        if (NEW_LINKED_LIST.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, LinkedList.class, arg);
        }
        if (NEW_COPY_ON_WRITE_ARRAY_LIST.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, CopyOnWriteArrayList.class, arg);
        }
        if (NEW_CONCURRENT_MAP.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, ConcurrentHashMap.class, arg);
        }
        if (NEW_HASH_MAP.matches(tree, state)
                && (paramTypes.isEmpty() || checkParamTypes(state, paramTypes, Map.class))) {
            return buildSingleArgFixSuggestion(tree, state, HashMap.class, arg);
        }
        if (NEW_HASH_MAP_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, HashMap.class, arg);
        }
        if (NEW_COPY_ON_WRITE_ARRAY_SET.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, CopyOnWriteArraySet.class, arg);
        }
        if (NEW_LINKED_HASH_SET.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, LinkedHashSet.class, arg);
        }
        if (NEW_LINKED_HASH_SET_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, LinkedHashSet.class, arg);
        }
        if (NEW_TREE_SET.matches(tree, state)
                && (paramTypes.isEmpty()
                        || checkParamTypes(state, paramTypes, Comparator.class)
                        || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, TreeSet.class, arg);
        }
        if (NEW_HASH_SET.matches(tree, state)
                && (paramTypes.isEmpty() || isIterableParamCollectionArg(state, paramTypes, argTypes))) {
            return buildSingleArgFixSuggestion(tree, state, HashSet.class, arg);
        }
        if (NEW_HASH_SET_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, HashSet.class, arg);
        }
        if (NEW_TREE_MAP.matches(tree, state)
                && (paramTypes.isEmpty()
                        || checkParamTypes(state, paramTypes, SortedMap.class)
                        || checkParamTypes(state, paramTypes, Comparator.class))) {
            return buildSingleArgFixSuggestion(tree, state, TreeMap.class, arg);
        }
        if (NEW_LINKED_HASH_MAP.matches(tree, state)
                && (paramTypes.isEmpty() || checkParamTypes(state, paramTypes, Map.class))) {
            return buildSingleArgFixSuggestion(tree, state, LinkedHashMap.class, arg);
        }
        if (NEW_LINKED_HASH_MAP_WITH_EXPECTED_SIZE.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, LinkedHashMap.class, arg);
        }
        if (NEW_ENUM_MAP.matches(tree, state)
                && (paramTypes.isEmpty()
                        || checkParamTypes(state, paramTypes, Class.class)
                        || checkParamTypes(state, paramTypes, Map.class))) {
            return buildSingleArgFixSuggestion(tree, state, EnumMap.class, arg);
        }
        if (NEW_IDENTITY_HASH_MAP.matches(tree, state)) {
            return buildSingleArgFixSuggestion(tree, state, IdentityHashMap.class, arg);
        }
        return Description.NO_MATCH;
    }

    private Description buildSingleArgFixSuggestion(
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
                .setMessage("The factory method call should be replaced with a constructor call.")
                .addFix(fixBuilder.replace(tree, replacement).build())
                .build();
    }

    private boolean isIterableParamCollectionArg(VisitorState state, List<Type> params, List<Type> args) {
        return checkParamTypes(state, params, Iterable.class)
                && checkArgTypes(state, args, Collection.class);
    }

    private boolean checkParamTypes(VisitorState state, List<Type> params, Class<?>... expected) {
        return checkTypes(state, false, params, expected);
    }

    private boolean checkArgTypes(VisitorState state, List<Type> args, Class<?>... expected) {
        return checkTypes(state, true, args, expected);
    }

    private boolean checkTypes(VisitorState state, boolean subtypes, List<Type> typeList1, Class<?>... typeList2) {
        Types types = state.getTypes();
        if (typeList1.size() != typeList2.length) {
            return false;
        }
        for (int i = 0; i < typeList1.size(); i++) {
            Type t1 = types.erasure(typeList1.get(i));
            Type t2 = types.erasure(state.getTypeFromString(typeList2[i].getName()));
            if ((!subtypes && !types.isSameType(t1, t2)) || (subtypes && !types.isSubtype(t1, t2))) {
                return false;
            }
        }
        return true;
    }
}
