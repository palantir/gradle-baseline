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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class CompileTimeConstantViolatesLiskovSubstitutionTest {

    @Test
    public void testInterface_negative() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(@CompileTimeConstant String value);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAbstractClass_negative() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  static abstract class A {",
                        "    public abstract void foo(@CompileTimeConstant String value);",
                        "  }",
                        "  static class B extends A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testClass_negative() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  static class A {",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "  static class B extends A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testImplementsAnnotated_positive() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(@CompileTimeConstant String value);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(String value) {}",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(@CompileTimeConstant String value);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testExtendsAnnotated_positive() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  static class A {",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "  static class B extends A {",
                        "    @Override",
                        "    public void foo(String value) {}",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  static class A {",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "  static class B extends A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testImplementsAnnotated_twoParametersFail_positive() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(@CompileTimeConstant String a, @CompileTimeConstant String b);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(String a, String b) {}",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(@CompileTimeConstant String a, @CompileTimeConstant String b);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(@CompileTimeConstant String a, @CompileTimeConstant String b) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testSimpleImplementsUnannotated_positive() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(String value);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "// BUG: Diagnostic contains: must also be applied to the super method",
                        "    public void foo(@CompileTimeConstant String value) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testSimpleImplementsUnannotated_multipleParameters_positive() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CompileTimeConstant;",
                        "class Test {",
                        "  interface A {",
                        "    void foo(String a, String b);",
                        "  }",
                        "  static class B implements A {",
                        "    @Override",
                        "    public void foo(",
                        "        String a,",
                        "// BUG: Diagnostic contains: must also be applied to the super method",
                        "        @CompileTimeConstant String b) {}",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(CompileTimeConstantViolatesLiskovSubstitution.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(CompileTimeConstantViolatesLiskovSubstitution.class, getClass());
    }
}
