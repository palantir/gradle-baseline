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

public class PreferBuiltInConcurrentKeySetTests {

    @Test
    public void detect() {
        CompilationTestHelper.newInstance(PreferBuiltInConcurrentKeySet.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Sets;",
                        "class Test {",
                        "  void foo() {",
                        "    // BUG: Diagnostic contains: Prefer Java's built-in Concurrent Set implementation",
                        "    Sets.newConcurrentHashSet();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_iterables() {
        CompilationTestHelper.newInstance(PreferBuiltInConcurrentKeySet.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.Sets;",
                        "class Test {",
                        "  void foo() {",
                        "    Sets.newConcurrentHashSet(com.google.common.collect.ImmutableList.of());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void auto_fix() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferBuiltInConcurrentKeySet(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.google.common.collect.Sets;",
                        "class Test {",
                        "  void foo() {",
                        "    Sets.newConcurrentHashSet();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Sets;",
                        "import java.util.concurrent.ConcurrentHashMap;",
                        "class Test {",
                        "  void foo() {",
                        "    ConcurrentHashMap.newKeySet();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
