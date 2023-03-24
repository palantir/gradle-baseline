/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CollectionsIsEmptyTest {
    @Test
    public void test_size_equals_zero() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    // BUG: Diagnostic contains: foo.isEmpty()",
                        "    return foo.size() == 0;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_size_does_not_equal_zero() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    // BUG: Diagnostic contains: !foo.isEmpty()",
                        "    return foo.size() != 0;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_zero_equals_size() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    // BUG: Diagnostic contains: foo.isEmpty()",
                        "    return 0 == foo.size();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_zero_does_not_equal_size() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    // BUG: Diagnostic contains: !foo.isEmpty()",
                        "    return 0 != foo.size();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_other_collection_types() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + Set.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(Set<String> foo) {",
                        "    // BUG: Diagnostic contains: !foo.isEmpty()",
                        "    return foo.size() != 0;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_conjunction() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo, List<String> bar) {",
                        "    // BUG: Diagnostic contains: !foo.isEmpty() && !bar.isEmpty()",
                        "    return 0 != foo.size() && 0 != bar.size();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_no_match() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.size() == 1 && foo.size() != 1 && 1 == foo.size() && 1 != foo.size();",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(CollectionsIsEmpty.class, getClass());
    }
}
