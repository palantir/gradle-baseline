/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import org.junit.Before;
import org.junit.Test;

public final class DangerousIterablesPartitionUsageTests {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousIterablesPartitionUsage.class, getClass());
    }

    @Test
    public void shouldNotUseIterablesPartition() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.lang.Iterable;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    // BUG: Diagnostic contains: Prefer Lists.partition",
                        "    return Iterables.partition(list, 10);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldUseListsPartition() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Lists;",
                        "import java.lang.Iterable;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Lists.partition(list, 10);",
                        "  }",
                        "}")
                .expectNoDiagnostics()
                .doTest();
    }

}
