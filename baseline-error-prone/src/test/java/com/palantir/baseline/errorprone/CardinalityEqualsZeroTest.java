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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CardinalityEqualsZeroTest {
    // private static final boolean EM = MYLIST.size()
    @Test
    public void test_size_equals_zero() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.size() == 0;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_size_does_not_equal_zero() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.size() != 0;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return !foo.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_zero_equals_size() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return 0 == foo.size();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_zero_does_not_equal_size() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return 0 != foo.size();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return !foo.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_other_collection_types() {
        fix().addInputLines(
                        "Test.java",
                        "import " + Set.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(Set<String> foo) {",
                        "    return foo.size() != 0;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Set.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(Set<String> foo) {",
                        "    return !foo.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_conjunction() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo, List<String> bar) {",
                        "    return 0 != foo.size() && 0 != bar.size();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo, List<String> bar) {",
                        "    return !foo.isEmpty() && !bar.isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_not_in_method() {
        // Ensure instances of `size() == 0` are matched when they're enclosed in a function call, or a
        // variable assignment.
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  private static boolean H = List.of(\"Hello\").size() == 0;",
                        "  static boolean g(boolean x) { return x; }",
                        "  static boolean f(List<String> foo) {",
                        "    return g(foo.size() == 0);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  private static boolean H = List.of(\"Hello\").isEmpty();",
                        "  static boolean g(boolean x) { return x; }",
                        "  static boolean f(List<String> foo) {",
                        "    return g(foo.isEmpty());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_no_match() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static boolean f(List<String> foo) {",
                        "    return foo.size() == 1 && foo.size() != 1 && 1 == foo.size() && 1 != foo.size();",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void test_implementation_of_isEmpty() {
        fix().addInputLines(
                        "TestCollection.java",
                        "import " + ArrayList.class.getCanonicalName() + ";",
                        "class TestCollection extends ArrayList<String> {",
                        "  @Override",
                        "  public boolean isEmpty() {",
                        "    return size() == 0;",
                        "  }",
                        "  public boolean customIsEmpty() {",
                        "    return size() == 0;",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestCollection.java",
                        "import " + ArrayList.class.getCanonicalName() + ";",
                        "class TestCollection extends ArrayList<String> {",
                        "  @Override",
                        "  public boolean isEmpty() {",
                        "    return size() == 0;",
                        "  }",
                        "  public boolean customIsEmpty() {",
                        "    return isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_implementation_of_isEmpty_using_this() {
        fix().addInputLines(
                        "TestCollection.java",
                        "import " + ArrayList.class.getCanonicalName() + ";",
                        "class TestCollection extends ArrayList<String> {",
                        "  @Override",
                        "  public boolean isEmpty() {",
                        "    return this.size() == 0;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void test_implementation_of_isEmpty_with_other_collection() {
        fix().addInputLines(
                        "TestCollection.java",
                        "import " + ArrayList.class.getCanonicalName() + ";",
                        "import " + List.class.getCanonicalName() + ";",
                        "class TestCollection extends ArrayList<String> {",
                        "  @Override",
                        "  public boolean isEmpty() {",
                        "    return this.size() == 0 && List.of(\"test\").size() == 0;",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestCollection.java",
                        "import " + ArrayList.class.getCanonicalName() + ";",
                        "import " + List.class.getCanonicalName() + ";",
                        "class TestCollection extends ArrayList<String> {",
                        "  @Override",
                        "  public boolean isEmpty() {",
                        "    return this.size() == 0 && List.of(\"test\").isEmpty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_size_on_non_collection() {
        fix().addInputLines(
                        "TestNonCollection.java",
                        "class TestNonCollection {",
                        "  public int size() {",
                        "    return 0;",
                        "  }",
                        "  public boolean myIsEmpty() {",
                        "    return size() == 0;",
                        "  }",
                        "  public boolean anotherIsEmpty() {",
                        "    return this.size() == 0;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(CardinalityEqualsZero.class, getClass());
    }

    @SuppressWarnings("checkstyle:IllegalType")
    static class MyList extends ArrayList<String> {

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        public boolean myIsNotEmpty() {
            return size() != 0;
        }
    }
}
