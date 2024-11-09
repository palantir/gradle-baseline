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

import org.junit.jupiter.api.Test;

class StreamToListTest {
    @Test
    public void test() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  List<String> f0(Stream<String> in) {",
                        "    return in.toList();",
                        "  }",
                        // Collectors.toList() supports nulls & is mutable while Stream#toList() does not
                        "  List<String> f1(Stream<String> in) {",
                        "    return in.collect(Collectors.toList());",
                        "  }",
                        "  List<String> f2(Stream<String> in) {",
                        "    return in.collect(Collectors.toUnmodifiableList());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  List<String> f0(Stream<String> in) {",
                        "    return in.toList();",
                        "  }",
                        "  List<String> f1(Stream<String> in) {",
                        "    return in.collect(Collectors.toList());",
                        "  }",
                        "  List<String> f2(Stream<String> in) {",
                        "    return in.toList();",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(StreamToList.class, getClass());
    }
}
