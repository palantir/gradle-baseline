/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

class VisibleForTestingPackagePrivateTest {

    @Test
    void testMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  public void foo() {}",
                        "  @VisibleForTesting",
                        "  public static void staticFoo() {}",
                        "  @VisibleForTesting",
                        "  protected void bar() {}",
                        "  public void baz() {}",
                        "  public static void staticBaz() {}",
                        "  protected void bang() {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  void foo() {}",
                        "  @VisibleForTesting",
                        "  static void staticFoo() {}",
                        "  @VisibleForTesting",
                        "  void bar() {}",
                        "  public void baz() {}",
                        "  public static void staticBaz() {}",
                        "  protected void bang() {}",
                        "}")
                .doTest();
    }

    @Test
    void testField() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  public int foo = 1;",
                        "  public int bar = 1;",
                        "  @VisibleForTesting",
                        "  public static final int FOO = 1;",
                        "  public static final int BAR = 1;",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  int foo = 1;",
                        "  public int bar = 1;",
                        "  @VisibleForTesting",
                        "  static final int FOO = 1;",
                        "  public static final int BAR = 1;",
                        "}")
                .doTest();
    }

    @Test
    void testType() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  public static final class Foo {}",
                        "  public static final class Bar {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public class Test {",
                        "  @VisibleForTesting",
                        "  static final class Foo {}",
                        "  public static final class Bar {}",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeInterfaceMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public interface Test {",
                        "  @VisibleForTesting",
                        "  default void foo() {}",
                        "  @VisibleForTesting",
                        "  static void staticFoo() {}",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testNegativeInterfaceFields() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public interface Test {",
                        "  @VisibleForTesting",
                        "  int FOO = 5;",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new VisibleForTestingPackagePrivate(), getClass());
    }
}
