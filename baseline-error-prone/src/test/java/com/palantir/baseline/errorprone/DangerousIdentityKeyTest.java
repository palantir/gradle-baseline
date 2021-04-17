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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DangerousIdentityKeyTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousIdentityKey.class, getClass());
    }

    @Test
    public void testInvalidMapKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override equals",
                        "        return new HashMap<Pattern, String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidSetKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override equals",
                        "        return new HashSet<Pattern>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testValidMap() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        return new IdentityHashMap<Pattern, String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testValidSetKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    private Object test() {",
                        "        return new HashSet<String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testValidNonFinal() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    static class Impl {}",
                        "    private Object test() {",
                        "        return new HashSet<Impl>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testValidEnum() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    enum Impl {",
                        "        INSTANCE",
                        "    }",
                        "    private Object test() {",
                        "        return new HashSet<Impl>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidNoEquals() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    static final class Impl {",
                        "        @Override public boolean equals(Object o) {",
                        "            return true;",
                        "        }",
                        "    }",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override",
                        "        return new HashSet<Impl>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidNoHash() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    static final class Impl {",
                        "        @Override public int hashCode() {",
                        "            return 1;",
                        "        }",
                        "    }",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override",
                        "        return new HashSet<Impl>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testObjectAllowed() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    private Object test() {",
                        "        return new HashSet<Object>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testRawTypeAllowed() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    private Object test() {",
                        "        return new HashSet();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testWildcardAllowed() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "    private HashSet<?> test() {",
                        "        return new HashSet<>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCollectMapInvalidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "    private Map<Pattern, String> test() {",
                        "        return Stream.of(\".\").collect(",
                        "                // BUG: Diagnostic contains: does not override",
                        "                Collectors.toMap(Pattern::compile, s -> s));",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCollectMapValidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "    private Map<String, Pattern> test() {",
                        "        return Stream.of(\".\").collect(",
                        "                Collectors.toMap(s -> s, Pattern::compile));",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCollectMapInvalidKey_immutable() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.*;",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "    private Map<Pattern, String> test() {",
                        "        return Stream.of(\".\").collect(",
                        "                // BUG: Diagnostic contains: does not override",
                        "                ImmutableMap.toImmutableMap(Pattern::compile, s -> s));",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCollectMapValidKey_immutable() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.*;",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "    private Map<String, Pattern> test() {",
                        "        return Stream.of(\".\").collect(",
                        "                ImmutableMap.toImmutableMap(s -> s, Pattern::compile));",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testGuavaCacheInvalidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.cache.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override",
                        "        return CacheBuilder.newBuilder().<Pattern, String>build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testGuavaCacheValidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.cache.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        return CacheBuilder.newBuilder().<String, Pattern>build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCaffeineCacheInvalidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.github.benmanes.caffeine.cache.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: does not override",
                        "        return Caffeine.newBuilder().<Pattern, String>build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCaffeineCacheValidKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.github.benmanes.caffeine.cache.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        return Caffeine.newBuilder().<String, Pattern>build();",
                        "    }",
                        "}")
                .doTest();
    }
}
