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

public final class PreferInputStreamTransferToTests {

    @Test
    public void should_not_use_Guava_ByteStreams_copy() {
        CompilationTestHelper.newInstance(PreferInputStreamTransferTo.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    // BUG: Diagnostic contains: Prefer InputStream.transferTo(OutputStream)",
                        "    return com.google.common.io.ByteStreams.copy"
                                + "(InputStream.nullInputStream(), OutputStream.nullOutputStream());",
                        "  }",
                        "}")
                .doTest();

        CompilationTestHelper.newInstance(PreferInputStreamTransferTo.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    // BUG: Diagnostic contains: Prefer InputStream.transferTo(OutputStream)",
                        "    return com.google.common.io.ByteStreams.copy(this, output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void may_use_InputStream_transferTo_OutputStream() {
        CompilationTestHelper.newInstance(PreferInputStreamTransferTo.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    return InputStream.nullInputStream().transferTo(OutputStream.nullOutputStream());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void auto_fix_Guava_ByteStreams_copy() {
        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return com.google.common.io.ByteStreams.copy(in, out);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return in.transferTo(out);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    return com.google.common.io.ByteStreams.copy"
                                + "(InputStream.nullInputStream(), OutputStream.nullOutputStream());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    return InputStream.nullInputStream().transferTo(OutputStream.nullOutputStream());",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return com.google.common.io.ByteStreams.copy(this, output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return super.transferTo(output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  InputStream input() {",
                        "    return InputStream.nullInputStream();",
                        "  }",
                        "  OutputStream output() {",
                        "    return OutputStream.nullOutputStream();",
                        "  }",
                        "  long f() throws java.io.IOException {",
                        "    return com.google.common.io.ByteStreams.copy(this.input(), this.output());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  InputStream input() {",
                        "    return InputStream.nullInputStream();",
                        "  }",
                        "  OutputStream output() {",
                        "    return OutputStream.nullOutputStream();",
                        "  }",
                        "  long f() throws java.io.IOException {",
                        "    return this.input().transferTo(this.output());",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return com.google.common.io.ByteStreams.copy(this.delegate(), output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "",
                        "  InputStream delegate() {",
                        "    return InputStream.nullInputStream();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return this.delegate().transferTo(output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "",
                        "  InputStream delegate() {",
                        "    return InputStream.nullInputStream();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void auto_fix_Apache_copy() {
        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return org.apache.commons.io.IOUtils.copy(in, out);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return in.transferTo(out);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return org.apache.commons.io.IOUtils.copyLarge(in, out);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test {",
                        "  long f() throws java.io.IOException {",
                        "    InputStream in = InputStream.nullInputStream();",
                        "    OutputStream out = OutputStream.nullOutputStream();",
                        "    return in.transferTo(out);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);

        RefactoringValidator.of(PreferInputStreamTransferTo.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return org.apache.commons.io.IOUtils.copyLarge(this, output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.io.InputStream;",
                        "import java.io.OutputStream;",
                        "class Test extends InputStream {",
                        "  @Override",
                        "  public long transferTo(OutputStream output) throws java.io.IOException {",
                        "    return super.transferTo(output);",
                        "  }",
                        "",
                        "  @Override",
                        "  public int read() throws java.io.IOException {",
                        "    return -1;",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
