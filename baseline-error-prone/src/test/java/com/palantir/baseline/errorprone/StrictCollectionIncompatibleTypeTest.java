/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class StrictCollectionIncompatibleTypeTest {

    @Test
    void testUnexpectedType_map() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.function.Function;",
                        "import java.util.Map;",
                        "import java.util.List;",
                        "class Test {",
                        "   String f0(Map<Integer, String> map, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return map.get(key);",
                        "   }",
                        "   boolean f1(Map<Integer, String> map, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return map.containsKey(key);",
                        "   }",
                        "   String f2(CustomMap map, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return map.get(key);",
                        "   }",
                        "   boolean f3(CustomMap map, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return map.containsKey(key);",
                        "   }",
                        "   String f4(CustomMap map, String key) {",
                        "       // BUG: Diagnostic contains: Value 'key' of type 'String' is not "
                                + "compatible with the expected type 'Integer'",
                        "       return map.remove(key);",
                        "   }",
                        "   Object f4(Map<Integer, String> map, List<String> keys) {",
                        "       // BUG: Diagnostic contains: Type 'String' is not compatible with the expected type"
                                + " 'Integer'",
                        "       return keys.stream().map(map::get);",
                        "   }",
                        "   Object f6(Map<CharSequence, String> map, List<String> keys) {",
                        "       return keys.stream().map(map::get);",
                        "   }",
                        "   Object f7(Map<String, String> map, List<CharSequence> keys) {",
                        "       return keys.stream().map(map::get);",
                        "   }",
                        "   Converter f8(CustomMap map, List<String> keys) {",
                        "       // This should fail once we implement a more general check",
                        "       return map::get;",
                        "   }",
                        "   interface CustomMap extends Map<Integer, String> {}",
                        "   interface Converter { String convert(Integer input); }",
                        "}")
                .doTest();
    }

    @Test
    void testUnexpectedType_collection() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.Collection;",
                        "import java.util.List;",
                        "import com.google.common.collect.ImmutableMap;",
                        "class Test {",
                        "   boolean f0(Collection<Integer> in, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.contains(key);",
                        "   }",
                        "   boolean f1(List<Integer> in, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.contains(key);",
                        "   }",
                        "   boolean f2(Custom in, Integer key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.contains(key);",
                        "   }",
                        "   boolean f3(ImmutableMap<Integer, String> in, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.keySet().contains(key);",
                        "   }",
                        "   boolean f4(List<Integer> in, String key) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.remove(key);",
                        "   }",
                        "   boolean f4(List<Integer> in) {",
                        "       // BUG: Diagnostic contains: incompatible types",
                        "       return in.remove(5L);",
                        "   }",
                        "   interface Custom extends List<CharSequence> {}",
                        "}")
                .doTest();
    }

    @Test
    void testCollectionContains_edges() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.Collection;",
                        "class Test {",
                        "   boolean f0(Collection<Integer> in) {",
                        "     return in.contains(null);",
                        "   }",
                        "   boolean f1(Collection<Integer> in) {",
                        "     return in.contains(3);",
                        "   }",
                        "   boolean f2(Collection<Class<? extends CharSequence>> in, Class<?> clazz) {",
                        "     return in.contains(clazz);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testAmbiguousMapKey_allowed() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.Map;",
                        "class Test {",
                        // Not recommended, but should not fail
                        "   Object f1(Map map, String key) {",
                        "       return map.get(key);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testAdditionalSuppressions() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.Map;",
                        "class Test {",
                        // idea style
                        "   @SuppressWarnings(\"SuspiciousMethodCalls\")",
                        "   Object f0(Map<Integer, Integer> map, String key) {",
                        "       return map.get(key);",
                        "   }",
                        // default error prone check suppression
                        "   @SuppressWarnings(\"CollectionIncompatibleType\")",
                        "   Object f1(Map<Integer, Integer> map, String key) {",
                        "       return map.get(key);",
                        "   }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(StrictCollectionIncompatibleType.class, getClass());
    }
}
