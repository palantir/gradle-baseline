/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

class CollectionStreamForEachTest {
    @Test
    public void test() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    in.stream().forEach(System.out::println);",
                        "    in.stream().forEachOrdered(System.out::println);",
                        "    in.stream().<String>forEach(System.out::println);",
                        "    in.stream().<String>forEachOrdered(System.out::println);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    in.forEach(System.out::println);",
                        "    in.forEach(System.out::println);",
                        "    in.<String>forEach(System.out::println);",
                        "    in.<String>forEach(System.out::println);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new CollectionStreamForEach(), getClass());
    }
}
