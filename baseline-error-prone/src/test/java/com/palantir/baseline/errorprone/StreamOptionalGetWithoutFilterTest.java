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
                        "    Stream<String> test1(Stream<Optional<String>> stream) {",
                        "        // BUG: Diagnostic contains filter(Optional::isPresent) before map(Optional::get)",
                        "        return stream.map(Optional::get);",
                        "    }",
                        "    Stream<String> test2(Stream<Optional<String>> stream) {",
                        "        // BUG: Diagnostic contains filter(Optional::isPresent) before map(Optional::get)",
                        "        return stream.map(opt -> opt.get());",
                        "    }",
                        "    Stream<String> test3(Stream<Optional<String>> stream) {",
                        "        // BUG: Diagnostic contains filter(Optional::isPresent) before map(Optional::get)"
                                + " or map(Optional::orElseThrow)",
                        "        return stream.map(opt -> opt.orElseThrow());",
                        "    }",
                        "    Stream<String> test4(Stream<Optional<String>> stream) {",
                        "        // BUG: Diagnostic contains filter(Optional::isPresent) before map(Optional::get)"
                                + " or map(Optional::orElseThrow)",
                        "        return stream.map(Optional::orElseThrow);",
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
                        "    Stream<String> test1(Stream<Optional<String>> stream) {",
                        "        return stream.filter(Optional::isPresent)",
                        "                .map(Optional::get);",
                        "    }",
                        "    Stream<String> test2(Stream<Optional<String>> stream) {",
                        "        return stream.filter(opt -> opt.isPresent())",
                        "                .map(opt -> opt.get());",
                        "    }",
                        "    Stream<String> test3(Stream<Optional<String>> stream) {",
                        "        return stream.filter(opt -> opt.isPresent())",
                        "                .map(Optional::get);",
                        "    }",
                        "    Stream<String> test4(Stream<Optional<String>> stream) {",
                        "        return stream.filter(opt -> opt.isPresent())",
                        "                .map(opt -> opt.get())",
                        "                .filter(s -> s.length() > 2);",
                        "    }",
                        "    Stream<String> test5(Stream<Optional<String>> stream) {",
                        "        return stream.filter(opt -> opt.isPresent())",
                        "                .map(Optional::orElseThrow);",
                        "    }",
                        "    Stream<String> test6(Stream<Optional<String>> stream) {",
                        "        return stream.filter(opt -> opt.isPresent())",
                        "                .map(opt -> opt.orElseThrow());",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper compile() {
        return CompilationTestHelper.newInstance(StreamOptionalGetWithoutFilter.class, getClass());
    }
}
