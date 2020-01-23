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

class ThrowSpecificityTest {

    @Test
    void fixUnnecessaryThrow_finalClass() {
        fix().addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "final class Test {",
                        "  void f0() throws Exception {",
                        "  }",
                        "  void f1() throws Throwable {",
                        "  }",
                        "  void f2() throws Exception {",
                        "    throw new IOException();",
                        "  }",
                        "  void f3() throws Throwable {",
                        "    throw new IOException();",
                        "  }",
                        "  void f4(boolean in) throws Throwable {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "  public void f5() throws Throwable {",
                        "    throw new IOException();",
                        "  }",
                        "  void f6(RuntimeException e) throws Throwable {",
                        "    Throwable cause = e.getCause();",
                        "    if (cause instanceof IllegalStateException) {",
                        "      throw cause;",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "final class Test {",
                        "  void f0() {}",
                        "  void f1() {}",
                        "  void f2() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  void f3() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  void f4(boolean in) throws ConnectException, FileNotFoundException {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "  public void f5() throws Throwable {",
                        "    throw new IOException();",
                        "  }",
                        "  void f6(RuntimeException e) throws Throwable {",
                        "    Throwable cause = e.getCause();",
                        "    if (cause instanceof IllegalStateException) {",
                        "      throw cause;",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void fixUnnecessaryThrow_private() {
        fix().addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "class Test {",
                        "  private void f0() throws Exception {",
                        "  }",
                        "  private void f1() throws Throwable {",
                        "  }",
                        "  private void f2() throws Exception {",
                        "    throw new IOException();",
                        "  }",
                        "  private void f3() throws Throwable {",
                        "    throw new IOException();",
                        "  }",
                        "  private void f4(boolean in) throws Throwable {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "class Test {",
                        "  private void f0() {}",
                        "  private void f1() {}",
                        "  private void f2() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  private void f3() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  private void f4(boolean in) throws ConnectException, FileNotFoundException {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void fixUnnecessaryThrow_static() {
        fix().addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "class Test {",
                        "  static void f0() throws Exception {",
                        "  }",
                        "  static void f1() throws Throwable {",
                        "  }",
                        "  static void f2() throws Exception {",
                        "    throw new IOException();",
                        "  }",
                        "  static void f3() throws Throwable {",
                        "    throw new IOException();",
                        "  }",
                        "  static void f4(boolean in) throws Throwable {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "class Test {",
                        "  static void f0() {}",
                        "  static void f1() {}",
                        "  static void f2() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  static void f3() throws IOException {",
                        "    throw new IOException();",
                        "  }",
                        "  static void f4(boolean in) throws ConnectException, FileNotFoundException {",
                        "    if (in) {",
                        "      throw new FileNotFoundException();",
                        "    } else {",
                        "      throw new ConnectException();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void nestedCatch() {
        fix().addInputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "abstract class Test {",
                        "  final void f() throws Exception {",
                        "    try {",
                        "      go();",
                        "    } catch (SocketException e) {",
                        "      throw new IOException(e);",
                        "    } catch (IOException e) {",
                        "      throw new RuntimeException();",
                        "    }",
                        "  }",
                        "  abstract void go() throws SocketException, IOException;",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.*;",
                        "import java.net.*;",
                        "abstract class Test {",
                        "  final void f() throws IOException {",
                        "    try {",
                        "      go();",
                        "    } catch (SocketException e) {",
                        "      throw new IOException(e);",
                        "    } catch (IOException e) {",
                        "      throw new RuntimeException();",
                        "    }",
                        "  }",
                        "  abstract void go() throws SocketException, IOException;",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ThrowSpecificity(), getClass());
    }
}
