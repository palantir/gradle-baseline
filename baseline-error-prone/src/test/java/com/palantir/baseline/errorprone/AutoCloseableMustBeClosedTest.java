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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class AutoCloseableMustBeClosedTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringTestHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(AutoCloseableMustBeClosed.class, getClass());
        refactoringTestHelper = RefactoringValidator.of(new AutoCloseableMustBeClosed(), getClass());
    }

    @Test
    public void testAutoCloseableReturn() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: should be annotated @MustBeClosed",
                        "    private AutoCloseable autoCloseable() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testInputStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: should be annotated @MustBeClosed",
                        "    private InputStream inputStream() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testOutputStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.io.*;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: should be annotated @MustBeClosed",
                        "    private OutputStream outputStream() {",
                        "        return new ByteArrayOutputStream();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testConstructor() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.io.*;",
                        "class Test extends FilterOutputStream {",
                        "    // BUG: Diagnostic contains: should be annotated @MustBeClosed",
                        "    public Test() {",
                        "        super(new ByteArrayOutputStream());",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testAlreadyAnnotated() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.*;",
                        "import java.io.*;",
                        "class Test {",
                        "    @MustBeClosed",
                        "    private Writer alreadyAnnotated() {",
                        "        return new StringWriter();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testCanIgnoreReturnValue() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.*;",
                        "import java.io.*;",
                        "class Test {",
                        "    @CanIgnoreReturnValue",
                        "    private Closeable ignoreReturn() {",
                        "        return new StringReader(\"\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testSuggestedFixAddMustBeClosed() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                        "import java.io.*;",
                        "class Test extends FilterOutputStream {",
                        "",
                        "    public Test() {",
                        "        super(new ByteArrayOutputStream());",
                        "    }",
                        "",
                        "    public static Test create() {",
                        "        return new Test();",
                        "    }",
                        "",
                        "    private AutoCloseable autoCloseable() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "",
                        "    private InputStream inputStream() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "",
                        "    private OutputStream outputStream() {",
                        "        return new ByteArrayOutputStream();",
                        "    }",
                        "",
                        "    @CanIgnoreReturnValue",
                        "    private Closeable ignoreReturn() {",
                        "        return new StringReader(\"\");",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                        "import com.google.errorprone.annotations.MustBeClosed;",
                        "import java.io.*;",
                        "class Test extends FilterOutputStream {",
                        "",
                        "    @MustBeClosed",
                        "    public Test() {",
                        "        super(new ByteArrayOutputStream());",
                        "    }",
                        "",
                        "    @MustBeClosed",
                        "    public static Test create() {",
                        "        return new Test();",
                        "    }",
                        "",
                        "    @MustBeClosed",
                        "    private AutoCloseable autoCloseable() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "",
                        "    @MustBeClosed",
                        "    private InputStream inputStream() {",
                        "        return new ByteArrayInputStream(new byte[0]);",
                        "    }",
                        "",
                        "    @MustBeClosed",
                        "    private OutputStream outputStream() {",
                        "        return new ByteArrayOutputStream();",
                        "    }",
                        "",
                        "    @CanIgnoreReturnValue",
                        "    private Closeable ignoreReturn() {",
                        "        return new StringReader(\"\");",
                        "    }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
