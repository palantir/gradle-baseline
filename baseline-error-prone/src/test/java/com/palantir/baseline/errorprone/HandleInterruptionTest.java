/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;


@Execution(ExecutionMode.CONCURRENT)
class HandleInterruptionTest {

    @Test
    void testExactMatchInterruptedException() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (InterruptedException e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (InterruptedException e) {",
                        "        Thread.currentThread().interrupt();",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testExactMatchInterruptedException_multicatch() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (InterruptedException e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (RuntimeException e) {",
                        "        throw e;",
                        "    } catch (InterruptedException e) {",
                        "        Thread.currentThread().interrupt();",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCatchBroad_exception() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (Exception e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (Exception e) {",
                        "        if (e instanceof InterruptedException) {",
                        "            Thread.currentThread().interrupt();",
                        "        }",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCatchBroad_union() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (RuntimeException | InterruptedException e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (RuntimeException | InterruptedException e) {",
                        "        if (e instanceof InterruptedException) {",
                        "            Thread.currentThread().interrupt();",
                        "        }",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCatchBroad_unionDoesNotMatch() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        File.createTempFile(\"a\", \"b\", new File(\".\"));",
                        "    } catch (RuntimeException | IOException e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCommentSuppression() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (InterruptedException e) {",
                        "        // interruption reset",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testDoesNotMatchTests() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f() {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "        assertThat(Thread.currentThread().getName()).isEqualTo(\"a\");",
                        "    } catch (InterruptedException e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testCatchBroad_instanceOfCheck() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (Exception e) {",
                        "        if (e instanceof InterruptedException) {",
                        "            throw e;",
                        "        }",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testRethrowDoesNotMatch() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        Thread.sleep(100);",
                        "    } catch (Exception e) {",
                        "        if (e instanceof RuntimeException) {",
                        "            throw new Exception(e);",
                        "        }",
                        "        throw e;",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testBroadCatch_doesNotThrowInterruptedException() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "public class Test {",
                        "  void f() throws Exception {",
                        "    try {",
                        "        new FileOutputStream(new File(\".\"));",
                        "    } catch (Exception e) {",
                        "        throw new RuntimeException(e);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new HandleInterruption(), getClass());
    }
}
