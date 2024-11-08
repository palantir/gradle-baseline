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

class StreamFlatmapOptionalTest {
    @Test
    public void test() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.Collection;",
                        "import java.util.List;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  List<String> f(List<List<Optional<String>>> in) {",
                        "    return in.stream().flatMap(Collection::stream).flatMap(Optional::stream).toList();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Collection;",
                        "import java.util.List;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  List<String> f(List<List<Optional<String>>> in) {",
                        "    return in.stream().flatMap(Collection::stream)"
                                + ".filter(Optional::isPresent).map(Optional::get).toList();",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(StreamFlatMapOptional.class, getClass());
    }
}
