/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

class StreamOptionalGetWithoutFilterTest {
    @Test
    public void testPositive() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "    void test1() {",
                        "        Stream<Optional<String>> stream = Stream.of(Optional.of(\"foo\"), Optional.empty());",
                        "        // BUG: Diagnostic contains \"Stream<Optional<?>> should call "
                                + "filter(Optional::isPresent) before map(Optional::get)\"",
                        "        stream.map(Optional::get)",
                        "                .forEach(System.out::println);",
                        "    }",
                        "    void test2() {",
                        "        Stream<Optional<Integer>> stream = Stream.of(Optional.of(1), Optional.empty());",
                        "        // BUG: Diagnostic contains \"Stream<Optional<?>> should call "
                                + "filter(Optional::isPresent) before map(Optional::get)\"",
                        "        stream.map(opt -> opt.get())",
                        "                .forEach(System.out::println);",
                        "    }",
                        "    void test3() {",
                        "        Stream<Optional<Double>> stream = Stream.of(Optional.of(1.0), Optional.empty());",
                        "        // BUG: Diagnostic contains \"Stream<Optional<?>> should call "
                                + "filter(Optional::isPresent) before map(Optional::get)\"",
                        "        stream.map(Optional::get)",
                        "                .forEach(System.out::println);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testNegative() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "    void test1() {",
                        "        Stream<Optional<String>> stream = Stream.of(Optional.of(\"foo\"), Optional.empty());",
                        "        stream.filter(Optional::isPresent)",
                        "                .map(Optional::get)",
                        "                .forEach(System.out::println); // No bug",
                        "    }",
                        "    void test2() {",
                        "        Stream<Optional<Integer>> stream = Stream.of(Optional.of(1), Optional.empty());",
                        "        stream.filter(opt -> opt.isPresent())",
                        "                .map(opt -> opt.get())",
                        "                .forEach(System.out::println); // No bug",
                        "    }",
                        "    void test3() {",
                        "        Stream<Optional<Double>> stream = Stream.of(Optional.of(1.0), Optional.empty());",
                        "        stream.filter(opt -> opt.isPresent())",
                        "                .map(Optional::get)",
                        "                .forEach(System.out::println); // No bug",
                        "    }",
                        "    void test4() {",
                        "        Stream<Optional<String>> stream = Stream.of(Optional.of(\"foo\"), Optional.empty());",
                        "        stream.filter(opt -> opt.isPresent())",
                        "                .map(opt -> opt.get())",
                        "                .filter(s -> s.length() > 2)",
                        "                .forEach(System.out::println); // No bug",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper compile() {
        return CompilationTestHelper.newInstance(StreamOptionalGetWithoutFilter.class, getClass());
    }
}
