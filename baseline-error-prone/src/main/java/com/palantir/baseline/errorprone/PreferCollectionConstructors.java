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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
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
import java.util.Map;
import java.util.Set;
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
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        summary =
                "Since Java 7 the standard collection constructors should be used instead of the static factory "
                        + "methods provided by Guava.")
public final class PreferCollectionConstructors extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newArrayList")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST_WITH_ITERABLE = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newArrayList")
            .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_ARRAY_LIST_WITH_CAPACITY = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newArrayListWithCapacity")
            .withParameters("int");

    private static final Matcher<ExpressionTree> NEW_LINKED_LIST = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newLinkedList")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_LIST_WITH_ITERABLE = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newLinkedList")
            .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_LIST = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Lists")
            .named("newCopyOnWriteArrayList")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_LIST_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Lists")
                    .named("newCopyOnWriteArrayList")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_CONCURRENT_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newConcurrentMap")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newHashMap")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_MAP_WITH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newHashMap")
            .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_TREE_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newTreeMap")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_TREE_MAP_WITH_COMPARATOR = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newTreeMap")
            .withParameters("java.util.Comparator");

    private static final Matcher<ExpressionTree> NEW_TREE_MAP_WITH_SORTED_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newTreeMap")
            .withParameters("java.util.SortedMap");

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_SET = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newCopyOnWriteArraySet")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_COPY_ON_WRITE_ARRAY_SET_WITH_ITERABLE =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Sets")
                    .named("newCopyOnWriteArraySet")
                    .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newLinkedHashSet")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_SET_WITH_ITERABLE = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newLinkedHashSet")
            .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_TREE_SET = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newTreeSet")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_TREE_SET_WITH_COMPARATOR = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newTreeSet")
            .withParameters("java.util.Comparator");

    private static final Matcher<ExpressionTree> NEW_TREE_SET_WITH_ITERABLE = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newTreeSet")
            .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_HASH_SET = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newHashSet")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_HASH_SET_WITH_ITERABLE = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Sets")
            .named("newHashSet")
            .withParameters("java.lang.Iterable");

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newLinkedHashMap")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_LINKED_HASH_MAP_WITH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newLinkedHashMap")
            .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newEnumMap")
            .withParameters();

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP_WITH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newEnumMap")
            .withParameters("java.util.Map");

    private static final Matcher<ExpressionTree> NEW_ENUM_MAP_WITH_CLASS = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newEnumMap")
            .withParameters("java.lang.Class");

    private static final Matcher<ExpressionTree> NEW_IDENTITY_HASH_MAP = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Maps")
            .named("newIdentityHashMap");

    private static final Map<Matcher<ExpressionTree>, Class<?>> classMap =
            ImmutableMap.<Matcher<ExpressionTree>, Class<?>>builder()
                    .put(NEW_ARRAY_LIST, ArrayList.class)
                    .put(NEW_HASH_SET, HashSet.class)
                    .put(NEW_HASH_MAP, HashMap.class)
                    .put(NEW_ARRAY_LIST_WITH_CAPACITY, ArrayList.class)
                    .put(NEW_LINKED_HASH_MAP, LinkedHashMap.class)
                    .put(NEW_TREE_MAP, TreeMap.class)
                    .put(NEW_CONCURRENT_MAP, ConcurrentHashMap.class)
                    .put(NEW_ARRAY_LIST_WITH_ITERABLE, ArrayList.class)
                    .put(NEW_LINKED_LIST, LinkedList.class)
                    .put(NEW_LINKED_LIST_WITH_ITERABLE, LinkedList.class)
                    .put(NEW_COPY_ON_WRITE_ARRAY_LIST, CopyOnWriteArrayList.class)
                    .put(NEW_COPY_ON_WRITE_ARRAY_LIST_WITH_ITERABLE, CopyOnWriteArrayList.class)
                    .put(NEW_HASH_MAP_WITH_MAP, HashMap.class)
                    .put(NEW_COPY_ON_WRITE_ARRAY_SET, CopyOnWriteArraySet.class)
                    .put(NEW_COPY_ON_WRITE_ARRAY_SET_WITH_ITERABLE, CopyOnWriteArraySet.class)
                    .put(NEW_LINKED_HASH_SET, LinkedHashSet.class)
                    .put(NEW_LINKED_HASH_SET_WITH_ITERABLE, LinkedHashSet.class)
                    .put(NEW_TREE_SET, TreeSet.class)
                    .put(NEW_TREE_SET_WITH_COMPARATOR, TreeSet.class)
                    .put(NEW_TREE_SET_WITH_ITERABLE, TreeSet.class)
                    .put(NEW_HASH_SET_WITH_ITERABLE, HashSet.class)
                    .put(NEW_TREE_MAP_WITH_SORTED_MAP, TreeMap.class)
                    .put(NEW_TREE_MAP_WITH_COMPARATOR, TreeMap.class)
                    .put(NEW_LINKED_HASH_MAP_WITH_MAP, LinkedHashMap.class)
                    .put(NEW_ENUM_MAP, EnumMap.class)
                    .put(NEW_ENUM_MAP_WITH_CLASS, EnumMap.class)
                    .put(NEW_ENUM_MAP_WITH_MAP, EnumMap.class)
                    .put(NEW_IDENTITY_HASH_MAP, IdentityHashMap.class)
                    .build();

    private static final Set<Matcher<ExpressionTree>> requiresCollectionArg = ImmutableSet.of(
            NEW_ARRAY_LIST_WITH_ITERABLE,
            NEW_LINKED_LIST_WITH_ITERABLE,
            NEW_COPY_ON_WRITE_ARRAY_LIST_WITH_ITERABLE,
            NEW_COPY_ON_WRITE_ARRAY_SET_WITH_ITERABLE,
            NEW_LINKED_HASH_SET_WITH_ITERABLE,
            NEW_TREE_SET_WITH_ITERABLE,
            NEW_HASH_SET_WITH_ITERABLE);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {

        Class<?> collectionClass = findCollectionClassToUse(state, tree);
        if (collectionClass == null) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        String collectionType = MoreSuggestedFixes.qualifyType(state, fixBuilder, collectionClass.getName());
        String typeArgs =
                tree.getTypeArguments().stream().map(state::getSourceForNode).collect(Collectors.joining(", "));
        String arg = tree.getArguments().isEmpty()
                ? ""
                : state.getSourceForNode(tree.getArguments().get(0));
        String replacement = "new " + collectionType + "<" + typeArgs + ">(" + arg + ")";
        return buildDescription(tree)
                .setMessage("The factory method call should be replaced with a constructor call. See"
                                + " https://github.com/palantir/gradle-baseline/blob/develop/docs/best-practices/java-coding-guidelines/readme.md#avoid-generics-clutter-where-possible"
                                + " for more information.")
                .addFix(fixBuilder.replace(tree, replacement).build())
                .build();
    }

    private Class<?> findCollectionClassToUse(VisitorState state, ExpressionTree tree) {
        for (Map.Entry<Matcher<ExpressionTree>, Class<?>> entry : classMap.entrySet()) {
            Matcher<ExpressionTree> matcher = entry.getKey();
            if (matcher.matches(tree, state)) {
                if (!requiresCollectionArg.contains(matcher) || isFirstArgCollection(state, tree)) {
                    return entry.getValue();
                }
                // All matchers are mutually exclusive, so no point in looking for another match.
                break;
            }
        }
        return null;
    }

    private boolean isFirstArgCollection(VisitorState state, ExpressionTree tree) {
        List<JCExpression> args = ((JCMethodInvocation) tree).args;
        if (args.isEmpty()) {
            return false;
        }
        Types types = state.getTypes();
        return types.isSubtype(
                types.erasure(args.get(0).type), types.erasure(state.getTypeFromString("java.util.Collection")));
    }
}
