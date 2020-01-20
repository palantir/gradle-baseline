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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class PreferCollectionConstructorsTest {

    @Test
    public void testNewArrayListRewrite() {
        testStaticFactoryMethodRewrite("Lists.newArrayList()", "new ArrayList<>()", "java.util.ArrayList");
    }

    @Test
    public void testNewArrayListWithCollectionsRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newArrayList(new java.util.ArrayList<>())",
                "new ArrayList<>(new java.util.ArrayList<>())",
                "java.util.ArrayList");
    }

    @Test
    public void testNewArrayListWithCapacityRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newArrayListWithCapacity(123)", "new ArrayList<>(123)", "java.util.ArrayList");
    }

    @Test
    public void testNewLinkedListRewrite() {
        testStaticFactoryMethodRewrite("Lists.newLinkedList()", "new LinkedList<>()", "java.util.LinkedList");
    }

    @Test
    public void testNewLinkedListWithCollectionsRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newLinkedList(new java.util.ArrayList<>())",
                "new LinkedList<>(new java.util.ArrayList<>())",
                "java.util.LinkedList");
    }

    @Test
    public void testNewCopyOnWriteListRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newCopyOnWriteArrayList()",
                "new CopyOnWriteArrayList<>()",
                "java.util.concurrent.CopyOnWriteArrayList");
    }

    @Test
    public void testNewCopyOnWriteArrayListWithCollectionsRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newCopyOnWriteArrayList(new java.util.ArrayList<>())",
                "new CopyOnWriteArrayList<>(new java.util.ArrayList<>())",
                "java.util.concurrent.CopyOnWriteArrayList");
    }

    @Test
    public void testNewConcurrentMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newConcurrentMap()", "new ConcurrentHashMap<>()", "java.util.concurrent.ConcurrentHashMap");
    }

    @Test
    public void testNewEnumMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newEnumMap(java.util.concurrent.TimeUnit.class)",
                "new EnumMap<>(java.util.concurrent.TimeUnit.class)",
                "java.util.EnumMap");
    }

    @Test
    public void testNewEnumMapWithMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newEnumMap(new java.util.HashMap<java.util.concurrent.TimeUnit, String>())",
                "new EnumMap<>(new java.util.HashMap<java.util.concurrent.TimeUnit, String>())",
                "java.util.EnumMap");
    }

    @Test
    public void testNewHashMapRewrite() {
        testStaticFactoryMethodRewrite("Maps.newHashMap()", "new HashMap<>()", "java.util.HashMap");
    }

    @Test
    public void testNewHashMapWithMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newHashMap(new java.util.HashMap<>())",
                "new HashMap<>(new java.util.HashMap<>())",
                "java.util.HashMap");
    }

    @Test
    public void testNewIdentityHashMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newIdentityHashMap()", "new IdentityHashMap<>()", "java.util.IdentityHashMap");
    }

    @Test
    public void testNewLinkedHashMapRewrite() {
        testStaticFactoryMethodRewrite("Maps.newLinkedHashMap()", "new LinkedHashMap<>()", "java.util.LinkedHashMap");
    }

    @Test
    public void testNewLinkedHashMapWithMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newLinkedHashMap(new java.util.HashMap<>())",
                "new LinkedHashMap<>(new java.util.HashMap<>())",
                "java.util.LinkedHashMap");
    }

    @Test
    public void testNewTreeMapRewrite() {
        testStaticFactoryMethodRewrite("Maps.newTreeMap()", "new TreeMap<>()", "java.util.TreeMap");
    }

    @Test
    public void testNewTreeMapWithMapRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newTreeMap(new java.util.TreeMap<>())",
                "new TreeMap<>(new java.util.TreeMap<>())",
                "java.util.TreeMap");
    }

    @Test
    public void testNewTreeMapWithComparatorRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.newTreeMap(java.util.Comparator.<String>naturalOrder())",
                "new TreeMap<>(java.util.Comparator.<String>naturalOrder())",
                "java.util.TreeMap");
    }

    @Test
    public void testNewCopyOnWriteArraySetRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newCopyOnWriteArraySet()",
                "new CopyOnWriteArraySet<>()",
                "java.util.concurrent.CopyOnWriteArraySet");
    }

    @Test
    public void testNewCopyOnWriteArraySetWithSetRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newCopyOnWriteArraySet(new java.util.HashSet<>())",
                "new CopyOnWriteArraySet<>(new java.util.HashSet<>())",
                "java.util.concurrent.CopyOnWriteArraySet");
    }

    @Test
    public void testNewHashSetRewrite() {
        testStaticFactoryMethodRewrite("Sets.newHashSet()", "new HashSet<>()", "java.util.HashSet");
    }

    @Test
    public void testNewHashSetWithSetRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newHashSet(new java.util.HashSet<>())",
                "new HashSet<>(new java.util.HashSet<>())",
                "java.util.HashSet");
    }

    @Test
    public void testNewLinkedHashSetRewrite() {
        testStaticFactoryMethodRewrite("Sets.newLinkedHashSet()", "new LinkedHashSet<>()", "java.util.LinkedHashSet");
    }

    @Test
    public void testNewLinkedHashSetWithSetRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newLinkedHashSet(new java.util.HashSet<>())",
                "new LinkedHashSet<>(new java.util.HashSet<>())",
                "java.util.LinkedHashSet");
    }

    @Test
    public void testNewTreeSetRewrite() {
        testStaticFactoryMethodRewrite("Sets.newTreeSet()", "new TreeSet<>()", "java.util.TreeSet");
    }

    @Test
    public void testNewTreeSetWithSetRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newTreeSet(new java.util.HashSet<>())",
                "new TreeSet<>(new java.util.HashSet<>())",
                "java.util.TreeSet");
    }

    @Test
    public void testNewTreeSetWithComparatorRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.newTreeSet(java.util.Comparator.naturalOrder())",
                "new TreeSet<>(java.util.Comparator.naturalOrder())",
                "java.util.TreeSet");
    }

    @Test
    public void testWithOneTypeArgRewrite() {
        testStaticFactoryMethodRewrite("Sets.<String>newHashSet()", "new HashSet<String>()", "java.util.HashSet");
    }

    @Test
    public void testWithTwoTypeArgsRewrite() {
        testStaticFactoryMethodRewrite(
                "Maps.<String, Integer>newTreeMap()", "new TreeMap<String, Integer>()", "java.util.TreeMap");
    }

    @Test
    public void testWithVarargRewrite() {
        testStaticFactoryMethodRewrite(
                "Lists.newArrayList(\"a\", \"b\", \"c\")", "Lists.newArrayList(\"a\", \"b\", \"c\")");
    }

    @Test
    public void testWithExplicitVarargInvocationRewrite() {
        testStaticFactoryMethodRewrite(
                "Sets.<java.util.Set<String>>newHashSet(new java.util.HashSet<String>())",
                "Sets.<java.util.Set<String>>newHashSet(new java.util.HashSet<String>())");
    }

    private void testStaticFactoryMethodRewrite(String before, String after, String... addedImports) {
        List<String> imports = Arrays.asList(
                "import com.google.common.collect.Iterables;",
                "import com.google.common.collect.Lists;",
                "import com.google.common.collect.Maps;",
                "import com.google.common.collect.Sets;");

        List<String> inputLines = new ArrayList<>(imports);
        inputLines.add("class Test {{ " + before + "; }}");

        List<String> outputLines = new ArrayList<>(imports);
        for (String addedImport : addedImports) {
            outputLines.add("import " + addedImport + ";");
        }
        outputLines.add("class Test {{ " + after + "; }}");

        RefactoringValidator.of(new PreferCollectionConstructors(), getClass())
                .addInputLines("Test.java", inputLines.toArray(new String[0]))
                .addOutputLines("Test.java", outputLines.toArray(new String[0]))
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
