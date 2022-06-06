/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

class DangerousCollapseKeysUsageTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousCollapseKeysUsage.class, getClass());
    }

    @Test
    public void should_error_when_collapse_keys_with_collector_is_used() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.Map;",
                        "import java.util.Set;",
                        "import java.util.stream.Collectors;",
                        "import one.util.streamex.EntryStream;",
                        "import org.apache.commons.lang3.tuple.Pair;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<Pair<Integer, Integer>> nodesAndPriorities = List.of(",
                        "                Pair.of(1, 5),",
                        "                Pair.of(1, 3),",
                        "                Pair.of(4, 2),",
                        "                Pair.of(1, 9));",
                        "        Map<Integer, Set<Integer>> nodesByPriority = EntryStream.of(nodesAndPriorities)",
                        "                .mapValues(Pair::getRight)",
                        "               // BUG: Diagnostic contains: collapseKeys API of EntryStream should be avoided",
                        "                .collapseKeys(Collectors.toSet())",
                        "                .toMap();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_error_when_collapse_keys_is_used_even_without_collector() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.Map;",
                        "import java.util.stream.Collectors;",
                        "import one.util.streamex.EntryStream;",
                        "import org.apache.commons.lang3.tuple.Pair;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<Pair<Integer, Integer>> nodesAndPriorities = List.of(",
                        "                Pair.of(1, 5),",
                        "                Pair.of(1, 3),",
                        "                Pair.of(4, 2),",
                        "                Pair.of(1, 9));",
                        "        Map<Integer, List<Integer>> nodesByPriority = EntryStream.of(nodesAndPriorities)",
                        "                .mapValues(Pair::getRight)",
                        "               // BUG: Diagnostic contains: collapseKeys API of EntryStream should be avoided",
                        "                .collapseKeys()",
                        "                .toMap();",
                        "   }",
                        "}")
                .doTest();
    }
}
