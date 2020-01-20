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

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import org.junit.jupiter.api.Test;

class ReadReturnValueIgnoredTest {

    @Test
    void testFix_stream_read() {
        fix().addInputLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   byte[] f(InputStream is) throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       is.read(buf);",
                        "       return buf;",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ByteStreams.class.getName() + ';',
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   byte[] f(InputStream is) throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       ByteStreams.readFully(is, buf);",
                        "       return buf;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_stream_skip() {
        fix().addInputLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   void f(InputStream is) throws IOException {",
                        "       is.skip(4);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ByteStreams.class.getName() + ';',
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   void f(InputStream is) throws IOException {",
                        "       ByteStreams.skipFully(is, 4);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_stream_complexAccessor() {
        fix().addInputLines(
                        "Test.java",
                        "import " + ByteArrayInputStream.class.getName() + ';',
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   byte[] f() throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       getStream().read(buf);",
                        "       return buf;",
                        "   }",
                        "   InputStream getStream() {",
                        "       return new ByteArrayInputStream(new byte[0]);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ByteStreams.class.getName() + ';',
                        "import " + ByteArrayInputStream.class.getName() + ';',
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   byte[] f() throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       ByteStreams.readFully(getStream(), buf);",
                        "       return buf;",
                        "   }",
                        "   InputStream getStream() {",
                        "       return new ByteArrayInputStream(new byte[0]);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_randomAccessFile() {
        fix().addInputLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + RandomAccessFile.class.getName() + ';',
                        "class Test {",
                        "   byte[] f(RandomAccessFile raf) throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       raf.read(buf);",
                        "       return buf;",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + RandomAccessFile.class.getName() + ';',
                        "class Test {",
                        "   byte[] f(RandomAccessFile raf) throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       raf.readFully(buf);",
                        "       return buf;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_reader_skip() {
        fix().addInputLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + Reader.class.getName() + ';',
                        "class Test {",
                        "   void f(Reader reader) throws IOException {",
                        "       reader.skip(4);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + CharStreams.class.getName() + ';',
                        "import " + IOException.class.getName() + ';',
                        "import " + Reader.class.getName() + ';',
                        "class Test {",
                        "   void f(Reader reader) throws IOException {",
                        "       CharStreams.skipFully(reader, 4);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testStream_singleByte() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   int getSecondByte(InputStream is) throws IOException {",
                        "       // BUG: Diagnostic contains: The result of a read call must be checked",
                        "       is.read();",
                        "       return is.read();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testRandomAccessFile_singleByte() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + RandomAccessFile.class.getName() + ';',
                        "class Test {",
                        "   int getSecondByte(RandomAccessFile raf) throws IOException {",
                        "       // BUG: Diagnostic contains: The result of a read call must be checked",
                        "       raf.read();",
                        "       return raf.read();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testReader_single() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + Reader.class.getName() + ';',
                        "class Test {",
                        "   int getSecondChar(Reader reader) throws IOException {",
                        "       // BUG: Diagnostic contains: The result of a read call must be checked",
                        "       reader.read();",
                        "       return reader.read();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testRandomAccessFile_skipBytes() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + RandomAccessFile.class.getName() + ';',
                        "class Test {",
                        "   void getSecondByte(RandomAccessFile raf) throws IOException {",
                        "       // BUG: Diagnostic contains: The result of a read call must be checked",
                        "       raf.skipBytes(3);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testNegative() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + IOException.class.getName() + ';',
                        "import " + InputStream.class.getName() + ';',
                        "class Test {",
                        "   int f(InputStream is) throws IOException {",
                        "       byte[] buf = new byte[4];",
                        "       return is.read(buf);",
                        "   }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ReadReturnValueIgnored(), getClass());
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ReadReturnValueIgnored.class, getClass());
    }
}
