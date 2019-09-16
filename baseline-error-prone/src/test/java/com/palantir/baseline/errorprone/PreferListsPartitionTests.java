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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;

public final class PreferListsPartitionTests {

    @Test
    public void should_not_use_Iterables_partition_for_List() {
        CompilationTestHelper.newInstance(PreferListsPartition.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
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
    public void may_use_Iterables_partition_for_Iterable() {
        CompilationTestHelper.newInstance(PreferListsPartition.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "class Test {",
                        "  Iterable<?> f(Iterable<?> iterable) {",
                        "    return Iterables.partition(iterable, 10);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void may_use_Iterables_partition_for_Set() {
        CompilationTestHelper.newInstance(PreferListsPartition.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.Set;",
                        "class Test {",
                        "  Iterable<?> f(Set<?> set) {",
                        "    return Iterables.partition(set, 10);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_use_Lists_partition() {
        CompilationTestHelper.newInstance(PreferListsPartition.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Lists;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Lists.partition(list, 10);",
                        "  }",
                        "}")
                .expectNoDiagnostics()
                .doTest();
    }

    @Test
    public void auto_fix_Iterables_partition() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferListsPartition(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Iterables.partition(list, 10);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import com.google.common.collect.Lists;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Lists.partition(list, 10);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

}
