/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

// Portions adapted from
// https://github.com/google/error-prone/blob/e4769fd/core/src/test/java/com/google/errorprone/bugpatterns/InputStreamSlowMultibyteReadTest.java
// Copyright 2014 The Error Prone Authors.

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class FilterOutputStreamSlowMultibyteWriteTest {

    private final CompilationTestHelper compilationHelper =
            CompilationTestHelper.newInstance(FilterOutputStreamSlowMultibyteWrite.class, getClass());

    @Test
    public void doingItRight() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "  public void write(byte[] b, int a, int c) {}",
                        "  public void write(int b) {}",
                        "}")
                .doTest();
    }

    @Test
    public void empty() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "  // BUG: Diagnostic contains:",
                        "class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "}")
                .doTest();
    }

    @Test
    public void basic() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "  // BUG: Diagnostic contains:",
                        "  public void write(int b) {}",
                        "}")
                .doTest();
    }

    @Test
    public void close() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "  // BUG: Diagnostic contains:",
                        "final class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "  @Override public void close() throws IOException {}",
                        "}")
                .doTest();
    }

    @Test
    public void abstractOverride() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "abstract class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "  // BUG: Diagnostic contains:",
                        "  public abstract void write(int b);",
                        "}")
                .doTest();
    }

    @Test
    public void nativeOverride() {
        compilationHelper
                .addSourceLines(
                        "TestClass.java",
                        "import java.io.*;",
                        "abstract class TestClass extends FilterOutputStream {",
                        "  TestClass(OutputStream out) { super(out); }",
                        "  // BUG: Diagnostic contains:",
                        "  public native void write(int b);",
                        "}")
                .doTest();
    }

    @Test
    public void inheritedMultiByteWrite() {
        compilationHelper
                .addSourceLines(
                        "Super.java",
                        "import java.io.*;",
                        "abstract class Super extends FilterOutputStream {",
                        "  Super() { super(new ByteArrayOutputStream()); }",
                        "  public void write(byte[] b, int a, int c) {}",
                        "}")
                .addSourceLines(
                        "TestClass.java",
                        "class TestClass extends Super {",
                        "  // BUG: Diagnostic contains:",
                        "  public void write(int b) {}",
                        "}")
                .doTest();
    }

    @Test
    public void inheritedSingleByteWrite() {
        compilationHelper
                .addSourceLines(
                        "Super.java",
                        "import java.io.*;",
                        "abstract class Super extends FilterOutputStream {",
                        "  Super() { super(new ByteArrayOutputStream()); }",
                        "  // BUG: Diagnostic contains:",
                        "  public void write(int b) {}",
                        "}")
                .addSourceLines(
                        "TestClass.java",
                        "class TestClass extends Super {",
                        "  public void write(byte[] b, int a, int c) {}",
                        "}")
                .doTest();
    }

    @Test
    public void inheritedSingleAndMultiByteWrite() {
        compilationHelper
                .addSourceLines(
                        "Super.java",
                        "import java.io.*;",
                        "abstract class Super extends FilterOutputStream {",
                        "  Super() { super(new ByteArrayOutputStream()); }",
                        "  public void write(int b) {}",
                        "  public void write(byte[] b, int a, int c) {}",
                        "}")
                .addSourceLines("TestClass.java", "class TestClass extends Super {}")
                .doTest();
    }

    @Test
    public void singleAndMultiByteWrite() {
        compilationHelper
                .addSourceLines(
                        "Super.java",
                        "import java.io.*;",
                        "  // BUG: Diagnostic contains:",
                        "abstract class Super extends FilterOutputStream {",
                        "  Super() { super(new ByteArrayOutputStream()); }",
                        "}")
                .addSourceLines(
                        "TestClass.java",
                        "class TestClass extends Super {",
                        "  public void write(int b) {}",
                        "  public void write(byte[] b, int a, int c) {}",
                        "}")
                .doTest();
    }
}
