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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.junit.jupiter.api.Test;

class ImmutableMapDuplicateKeyStrategyTest {

    @Test
    void testImmutableMap() {
        helper().addInputLines(
                        "Test.java",
                        "import " + ImmutableMap.class.getName() + ";",
                        "class Test {",
                        "  Object f(ImmutableMap.Builder<String, String> b) {",
                        "    return b.build();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableMap.class.getName() + ";",
                        "class Test {",
                        "  Object f(ImmutableMap.Builder<String, String> b) {",
                        "    return b.buildOrThrow();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testImmutableSortedMap() {
        helper().addInputLines(
                        "Test.java",
                        "import " + ImmutableSortedMap.class.getName() + ";",
                        "class Test {",
                        "  Object f(ImmutableSortedMap.Builder<String, String> b) {",
                        "    return b.build();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableSortedMap.class.getName() + ";",
                        "class Test {",
                        "  Object f(ImmutableSortedMap.Builder<String, String> b) {",
                        "    return b.buildOrThrow();",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator helper() {
        return RefactoringValidator.of(ImmutableMapDuplicateKeyStrategy.class, getClass());
    }
}
