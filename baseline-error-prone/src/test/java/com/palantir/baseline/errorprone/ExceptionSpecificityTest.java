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
import org.junit.jupiter.api.Test;

class ExceptionSpecificityTest {

    @Test
    void testFix_simple() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException | Error t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixMultipleCatchBlocks() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (Error t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixMultipleCatchBlocks_unnecessaryCatch() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (Exception t) {", // this is unreachable
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixMultipleCatchBlocks_unnecessaryCatch_finally() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (Exception t) {", // this is unreachable
                        "        System.out.println(\"foo\");",
                        "    } finally {",
                        "        System.out.println(\"finally\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } finally {",
                        "        System.out.println(\"finally\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_resourceDoesNotThrow() {
        fix()
                .addInputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (SafeCloseable ignored = SafeCloseable.INSTANCE) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "  enum SafeCloseable implements Closeable {",
                        "      INSTANCE;",
                        "      @Override public void close() {}",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (SafeCloseable ignored = SafeCloseable.INSTANCE) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException | Error t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "  enum SafeCloseable implements Closeable {",
                        "      INSTANCE;",
                        "      @Override public void close() {}",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_resourceThrowsUnchecked() {
        fix()
                .addInputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (SafeCloseable ignored = SafeCloseable.INSTANCE) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "  enum SafeCloseable implements Closeable {",
                        "      INSTANCE;",
                        "      @Override public void close() throws RuntimeException {}",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (SafeCloseable ignored = SafeCloseable.INSTANCE) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException | Error t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "  enum SafeCloseable implements Closeable {",
                        "      INSTANCE;",
                        "      @Override public void close() throws RuntimeException {}",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixWithUnreachableConditional() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Exception e) {",
                        // Unreachable, but the compiler doesn't flag it until we update the catch block.
                        "        if (e instanceof InterruptedException) {",
                        "            throw e;",
                        "        }",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.AST_MATCH);
    }

    @Test
    void testFixWithImpossibleInstanceOf() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  boolean f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "        return false;",
                        "    } catch (Exception e) {",
                        // Always results in false because nothing in the try block throws InterruptedException
                        "        return e instanceof InterruptedException;",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  boolean f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "        return false;",
                        "    } catch (RuntimeException e) {",
                        "        return false;",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixWithUnreachableConditional_else() {
        fix()
                .addInputLines("Test.java",
                        "class Test {",
                        "  void f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Exception e) {",
                        // Unreachable, but the compiler doesn't flag it until we update the catch block.
                        "        if (e instanceof InterruptedException) {",
                        "            throw e;",
                        "        } else {",
                        "            System.out.println(\"else\");",
                        "        }",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "class Test {",
                        "  void f(String param) throws Exception {",
                        "    try {",
                        "        System.out.println(\"hello\");",
                        "    } catch (RuntimeException e) {",
                        "        System.out.println(\"else\");",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.AST_MATCH);
    }

    @Test
    void testResource_creationThrows() {
        fix()
                .addInputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (OutputStream os = new FileOutputStream(new File(\"a\"))) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }


    @Test
    void testResource_closeThrows() {
        fix()
                .addInputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (OutputStream os = os()) {",
                        "        System.out.println(\"hello\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "  OutputStream os() {",
                        "    return new ByteArrayOutputStream();",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCheckedException() {
        fix()
                .addInputLines("Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        throw new IOException();",
                        "    } catch (Exception e) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void test_testCodeNotModified() {
        fix()
                .addInputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "        assertThat(Thread.currentThread().getName()).isEqualTo(\"test\");",
                        "    } catch (Throwable t) {",
                        "        System.out.println(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ExceptionSpecificity(), getClass());
    }
}
