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
                        "import java.util.*;",
                        "import java.util.stream.*;",
                        "public class Test {",
                        "  Stream<String> filter_empty(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isEmpty).map(o -> o.orElse(\"\"));",
                        "  }",
                        "  Stream<String> flatMap_simple(Stream<Optional<String>> in) {",
                        "    return in.flatMap(Optional::stream);",
                        "  }",
                        "  Stream<String> filter_map_get(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isPresent).map(Optional::get);",
                        "  }",
                        "  Stream<Integer> filter_map_length(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isPresent).map(o -> o.get().length());",
                        "  }",
                        "  Stream<String> filter_orElseThrow(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isPresent).map(Optional::orElseThrow);",
                        "  }",
                        "  Stream<String> flatMap_collection(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(Collection::stream).flatMap(Optional::stream);",
                        "  }",
                        "  Stream<String> flatMap_lambda_collection(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream().flatMap(Optional::stream));",
                        "  }",
                        "  Stream<String> flatMap_lambda_optional_collection("
                                + "Stream<Optional<Collection<Optional<String>>>> in) {",
                        "    return in.flatMap(o -> o.stream().flatMap(Collection::stream).flatMap(Optional::stream));",
                        "  }",
                        "  Stream<String> flatMap_simple_flatMap_collection(Stream<Optional<Collection<String>>> in) {",
                        "    return in.flatMap(Optional::stream).flatMap(Collection::stream);",
                        "  }",
                        "  Stream<String> flatMap_optional_flatMap_optional(Stream<Optional<Optional<String>>> in) {",
                        "    return in.flatMap(Optional::stream).flatMap(Optional::stream);",
                        "  }",
                        "  Collection<String> flatMap_optional_map_flatMap_optional_toList("
                                + "Stream<Optional<Map.Entry<Integer, Optional<String>>>> in) {",
                        "    return in.flatMap(Optional::stream).map(Map.Entry::getValue)"
                                + ".flatMap(Optional::stream).toList();",
                        "  }",
                        "  Collection<String> chain_flatMap_maps(Stream<Optional<String>> in) {",
                        "    return in.flatMap(Optional::stream)",
                        "      .map(Optional::ofNullable)",
                        "      .flatMap(Optional::stream)",
                        "      .map(Optional::ofNullable)",
                        "      .flatMap(Optional::stream)",
                        "      .toList();",
                        "  }",
                        "  Set<Integer> chain_flatMap_maps_toSet(Stream<Optional<Collection<Optional<String>>>> in) {",
                        "    return in.flatMap(Optional::stream)",
                        "      .map(Optional::ofNullable)",
                        "      .flatMap(Optional::stream)",
                        "      .flatMap(Collection::stream)",
                        "      .flatMap(Optional::stream)",
                        "      .map(String::length)",
                        "      .collect(Collectors.toSet());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.stream.*;",
                        "public class Test {",
                        "  Stream<String> filter_empty(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isEmpty).map(o -> o.orElse(\"\"));",
                        "  }",
                        "  Stream<String> flatMap_simple(Stream<Optional<String>> in) {",
                        "    return in.mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Stream<String> filter_map_get(Stream<Optional<String>> in) {",
                        "    return in.mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Stream<Integer> filter_map_length(Stream<Optional<String>> in) {",
                        "    return in.filter(Optional::isPresent).map(o -> o.get().length());",
                        "  }",
                        "  Stream<String> filter_orElseThrow(Stream<Optional<String>> in) {",
                        "    return in.mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Stream<String> flatMap_collection(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(Collection::stream).mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Stream<String> flatMap_lambda_collection(Stream<Collection<Optional<String>>> in) {",
                        "    return in.flatMap(list -> list.stream().mapMulti(Optional::ifPresent));",
                        "  }",
                        "  Stream<String> flatMap_lambda_optional_collection("
                                + "Stream<Optional<Collection<Optional<String>>>> in) {",
                        "    return in.flatMap(o -> "
                                + "o.stream().flatMap(Collection::stream).mapMulti(Optional::ifPresent));",
                        "  }",
                        "  Stream<String> flatMap_simple_flatMap_collection(Stream<Optional<Collection<String>>> in) {",
                        "    return in.<Collection<String>>mapMulti(Optional::ifPresent).flatMap(Collection::stream);",
                        "  }",
                        "  Stream<String> flatMap_optional_flatMap_optional(Stream<Optional<Optional<String>>> in) {",
                        "    return in.<Optional<String>>mapMulti(Optional::ifPresent)"
                                + ".mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Collection<String> flatMap_optional_map_flatMap_optional_toList("
                                + "Stream<Optional<Map.Entry<Integer, Optional<String>>>> in) {",
                        "    return in.<Map.Entry<Integer,Optional<String>>>mapMulti(Optional::ifPresent)"
                                + ".map(Map.Entry::getValue)"
                                + ".<String>mapMulti(Optional::ifPresent)"
                                + ".toList();",
                        "  }",
                        "  Collection<String> chain_flatMap_maps(Stream<Optional<String>> in) {",
                        "    return in.<String>mapMulti(Optional::ifPresent)",
                        "      .map(Optional::ofNullable)",
                        "      .<String>mapMulti(Optional::ifPresent)",
                        "      .map(Optional::ofNullable)",
                        "      .<String>mapMulti(Optional::ifPresent)",
                        "      .toList();",
                        "  }",
                        "  Set<Integer> chain_flatMap_maps_toSet(Stream<Optional<Collection<Optional<String>>>> in) {",
                        "    return in.<Collection<Optional<String>>>mapMulti(Optional::ifPresent)",
                        "      .map(Optional::ofNullable)",
                        "      .<Collection<Optional<String>>>mapMulti(Optional::ifPresent)",
                        "      .flatMap(Collection::stream)",
                        "      .<String>mapMulti(Optional::ifPresent)",
                        "      .map(String::length)",
                        "      .collect(Collectors.toSet());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testWildcards() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.stream.*;",
                        "public class Test {",
                        "  Stream<? extends Number> flatMap(Stream<Optional<? extends Number>> in) {",
                        "    return in.flatMap(Optional::stream);",
                        "  }",
                        "  Stream<? extends Number> map_maybe_flatMap(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber).flatMap(Optional::stream);",
                        "  }",
                        "  Set<? extends Number> flatMap_wildcard_producer_set(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber)",
                        "      .<Number>flatMap(Optional::stream)",
                        "      .collect(Collectors.toSet());",
                        "  }",
                        "  List<? extends Number> flatMap_wildcard_producer_list(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber)",
                        "      .<Number>flatMap(Optional::stream)",
                        "      .toList();",
                        "  }",
                        "  interface Wildcards {",
                        "    Map<String, Set<? extends Number>> numbers();",
                        "    private Set<? extends Number> getNumbers(Stream<String> in) {",
                        "      return in.map(string -> Optional.ofNullable(numbers().get(string)))",
                        "        .flatMap(Optional::stream)",
                        "        .findFirst()",
                        "        .orElseGet(Collections::emptySet);",
                        "    }",
                        "  }",
                        "  static <T extends Number> Optional<? extends Number> maybeNumber(T number) {",
                        "    return Optional.ofNullable(number);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.stream.*;",
                        "public class Test {",
                        "  Stream<? extends Number> flatMap(Stream<Optional<? extends Number>> in) {",
                        "    return in.mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Stream<? extends Number> map_maybe_flatMap(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber).mapMulti(Optional::ifPresent);",
                        "  }",
                        "  Set<? extends Number> flatMap_wildcard_producer_set(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber)",
                        "      .<Number>mapMulti(Optional::ifPresent)",
                        "      .collect(Collectors.toSet());",
                        "  }",
                        "  List<? extends Number> flatMap_wildcard_producer_list(Stream<? extends Number> in) {",
                        "    return in.map(Test::maybeNumber)",
                        "      .<Number>mapMulti(Optional::ifPresent)",
                        "      .toList();",
                        "  }",
                        "  interface Wildcards {",
                        "    Map<String, Set<? extends Number>> numbers();",
                        "    private Set<? extends Number> getNumbers(Stream<String> in) {",
                        "      return in.map(string -> Optional.ofNullable(numbers().get(string)))",
                        "        .<Set<? extends Number>>mapMulti(Optional::ifPresent)",
                        "        .findFirst()",
                        "        .orElseGet(Collections::emptySet);",
                        "    }",
                        "  }",
                        "  static <T extends Number> Optional<? extends Number> maybeNumber(T number) {",
                        "    return Optional.ofNullable(number);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(StreamFlatMapOptional.class, getClass());
    }
}
