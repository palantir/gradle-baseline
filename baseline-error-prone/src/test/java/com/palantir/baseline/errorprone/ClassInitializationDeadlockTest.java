/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.Test;

class ClassInitializationDeadlockTest {

    @Test
    void testStaticFieldNewSubtypeInstance() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  private static final Sub SUB = new Sub();",
                        "  static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticBlockNewSubtypeInstance() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  static {",
                        "    // BUG: Diagnostic contains: deadlock",
                        "    new Sub();",
                        "  }",
                        "  static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testNonStaticFieldAllowed() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  private final Sub SUB = new Sub();",
                        "  static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticFieldNewSubtypeInstancePrivateAllowed() {
        // This cas is handled to avoid false positives, however we don't
        // currently handle the case in which an intermediate public type
        // exists between Base and Sub.
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  private static final Sub SUB = new Sub();",
                        "  private static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticFieldNewSubtypeInstanceWithPrivateConstructorAllowed() {
        helper().addSourceLines(
                        "Base.java",
                        "interface Base {",
                        "  Sub SUB = new Sub();",
                        "  class Sub implements Base {",
                        "    private static final Object o = \"\";",
                        "    private Sub() {}",
                        "    private static void f() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticFieldNewSubtypeInstanceWithPrivateConstructorInstantiableThroughStaticMethod() {
        helper().addSourceLines(
                        "Base.java",
                        "interface Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  Sub SUB = new Sub();",
                        "  class Sub implements Base {",
                        "    private static final Object o = \"\";",
                        "    private Sub() {}",
                        // static method is not private
                        "    static void f() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticFieldNewSubtypeInstanceWithPrivateConstructorInstantiableThroughStaticField() {
        helper().addSourceLines(
                        "Base.java",
                        "interface Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  Sub SUB = new Sub();",
                        "  class Sub implements Base {",
                        //   static field is not private
                        "    static final Object o = \"\";",
                        "    private Sub() {}",
                        "    private static void f() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticBlockNewSubtypeInstancePrivateAllowed() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  static {",
                        "    new Sub();",
                        "  }",
                        "  private static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void deleteMe_exampleOnly() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  private static final Sub SUB = new Sub(\"val\");",
                        "  private final String value;",
                        "  Base(String value) {",
                        "    this.value = value;",
                        "  }",
                        "  static class Sub extends Base {",
                        "    Sub(String value) {",
                        "      super(value);",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testExampleUsingBroadFieldTypeExample() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  private static final Object SUB = new Sub();",
                        "  static class Sub extends Base {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFieldSubtypeStaticCall() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  private static final int VALUE = Sub.getInt();",
                        "  static class Sub extends Base {",
                        "    static int getInt() {",
                        "      return 1;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticBlockSubtypeStaticCall() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  static {",
                        "    // BUG: Diagnostic contains: deadlock",
                        "    Sub.getInt();",
                        "  }",
                        "  static class Sub extends Base {",
                        "    static int getInt() {",
                        "      return 1;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFieldSubtypeStaticFieldAccess() {
        helper().addSourceLines(
                        "Base.java",
                        "import java.util.Optional;",
                        "class Base {",
                        "  // BUG: Diagnostic contains: deadlock",
                        "  private static final Object VALUE = Sub.object;",
                        "  static class Sub extends Base {",
                        "    static Optional<?> object = Optional.empty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testBlockSubtypeStaticFieldAccess() {
        helper().addSourceLines(
                        "Base.java",
                        "import java.util.Optional;",
                        "class Base {",
                        "  static {",
                        "    // BUG: Diagnostic contains: deadlock",
                        "    Object value = Sub.object;",
                        "  }",
                        "  static class Sub extends Base {",
                        "    static Optional<?> object = Optional.empty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFieldSubtypeStaticFieldAccessAllowsConstants() {
        helper().addSourceLines(
                        "Base.java",
                        "import java.util.Optional;",
                        "class Base {",
                        "  private static final int VALUE = Sub.value;",
                        "  static class Sub extends Base {",
                        "    static final int value = 1;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testBlockSubtypeStaticFieldAccessAllowsConstants() {
        helper().addSourceLines(
                        "Base.java",
                        "import java.util.Optional;",
                        "class Base {",
                        "  static {",
                        "    int value = Sub.value;",
                        "  }",
                        "  static class Sub extends Base {",
                        "    static final int value = 1;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testBlockSubtypeStaticFieldAssignment() {
        helper().addSourceLines(
                        "Base.java",
                        "import java.util.Optional;",
                        "class Base {",
                        "  static {",
                        "    // BUG: Diagnostic contains: deadlock",
                        "    Sub.object = Optional.of(\"\");",
                        "  }",
                        "  static class Sub extends Base {",
                        "    static Optional<?> object = Optional.empty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticFieldAnonymousSubtypeAllowed() {
        helper().addSourceLines(
                        "Base.java",
                        "class Base {",
                        "  private static final Base OBJECT = new Base() {",
                        "    @Override",
                        "    public String toString() {",
                        "      return \"foo\";",
                        "    }",
                        "  };",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ClassInitializationDeadlock.class, getClass());
    }
}
