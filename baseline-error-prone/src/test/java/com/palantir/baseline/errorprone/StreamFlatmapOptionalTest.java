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
                        "import java.util.Optional;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Stream<String> f1(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(Collection::stream).flatMap(Optional::stream);",
                        "  }",
                        "  Stream<String> f2(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream().flatMap(Optional::stream));",
                        "  }",
                        "  Stream<String> f3(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream()).flatMap(Optional::stream);",
                        "  }",
                        "  Stream<String> f4(Stream<Optional<Optional<String>>> in) {",
                        "      return in.flatMap(Optional::stream).flatMap(Optional::stream);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Collection;",
                        "import java.util.Optional;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Stream<String> f1(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(Collection::stream)"
                                + ".filter(Optional::isPresent).map(Optional::orElseThrow);",
                        "  }",
                        "  Stream<String> f2(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream()"
                                + ".filter(Optional::isPresent).map(Optional::orElseThrow));",
                        "  }",
                        "  Stream<String> f3(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream())"
                                + ".filter(Optional::isPresent).map(Optional::orElseThrow);",
                        "  }",
                        "  Stream<String> f4(Stream<Optional<Optional<String>>> in) {",
                        "      return in.filter(Optional::isPresent).map(Optional::orElseThrow)"
                                + ".filter(Optional::isPresent).map(Optional::orElseThrow);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(StreamFlatMapOptional.class, getClass());
    }
}
