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
import org.junit.jupiter.api.Test;

public final class PreferCollectionTransformTests {

    @Test
    public void should_not_use_Iterables_transform_for_List() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    // BUG: Diagnostic contains: Prefer Lists.transform",
                        "    return Iterables.transform(list, x -> x);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_not_use_Iterables_transform_for_Collection() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.Collection;",
                        "class Test {",
                        "  Iterable<?> f(Collection<?> collection) {",
                        "    // BUG: Diagnostic contains: Prefer Collections2.transform",
                        "    return Iterables.transform(collection, x -> x);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_not_use_Iterables_transform_for_Set() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.Set;",
                        "class Test {",
                        "  Iterable<?> f(Set<?> set) {",
                        "    // BUG: Diagnostic contains: Prefer Collections2.transform",
                        "    return Iterables.transform(set, x -> x);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void may_use_Iterables_transform_for_Iterable() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "class Test {",
                        "  Iterable<?> f(Iterable<?> iterable) {",
                        "    return Iterables.transform(iterable, x -> x);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_use_Lists_transform() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Lists;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Lists.transform(list, x -> x);",
                        "  }",
                        "}")
                .expectNoDiagnostics()
                .doTest();
    }

    @Test
    public void should_use_Collections2_transform() {
        CompilationTestHelper.newInstance(PreferCollectionTransform.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Collections2;",
                        "import java.util.Collection;",
                        "class Test {",
                        "  Iterable<?> f(Collection<?> collection) {",
                        "    return Collections2.transform(collection, x -> x);",
                        "  }",
                        "}")
                .expectNoDiagnostics()
                .doTest();
    }

    @Test
    public void auto_fix_Iterables_transform_List() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferCollectionTransform(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Iterables.transform(list, x -> x);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import com.google.common.collect.Lists;",
                        "import java.util.List;",
                        "class Test {",
                        "  Iterable<?> f(List<?> list) {",
                        "    return Lists.transform(list, x -> x);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void auto_fix_Iterables_transform_Collection() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferCollectionTransform(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.Collection;",
                        "class Test {",
                        "  Iterable<?> f(Collection<?> collection) {",
                        "    return Iterables.transform(collection, x -> x);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Collections2;",
                        "import com.google.common.collect.Iterables;",
                        "import java.util.Collection;",
                        "class Test {",
                        "  Iterable<?> f(Collection<?> collection) {",
                        "    return Collections2.transform(collection, x -> x);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

}
