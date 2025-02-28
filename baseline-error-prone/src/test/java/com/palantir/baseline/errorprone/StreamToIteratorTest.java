/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

public final class StreamToIteratorTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(StreamToIterator.class, getClass());
    }

    @Test
    public void testDirectCase() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        Stream.of(1, 2, 3).iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testIndirectStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        List.of(1, 2, 3).stream().iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testStreamChain() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        Stream.of(1, 2, 3).map(x -> x + 1).flatMap(x -> Stream.of(x)).iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testStreamAsParameter() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test(Stream<Object> stream) {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        stream.iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testAsMethodReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test(Stream<Stream<Object>> stream) {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        stream.map(Stream::iterator);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testIntStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.IntStream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        IntStream.range(1, 3).iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testIntStreamReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.IntStream;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        Stream.of(IntStream.range(1, 3)).map(IntStream::iterator);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testSpliterator() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        Stream.of(1, 2, 3).spliterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testSpliteratorMethodReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test(Stream<Stream<Object>> stream) {",
                        "        // BUG: Diagnostic contains: StreamToIterator",
                        "        stream.map(Stream::spliterator);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testNoFalsePositiveOnNonStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "    private void test() {",
                        "        List.of(1, 2, 3).iterator();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testNoFalsePositiveOnNonStreamReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    private void test() {",
                        "        Stream.of(List.of(1, 2, 3)).map(List::iterator);",
                        "    }",
                        "}")
                .doTest();
    }
}
