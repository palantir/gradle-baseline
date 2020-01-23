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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class SafeLoggingExceptionMessageFormatTests {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(SafeLoggingExceptionMessageFormat.class, getClass());
    }

    @Test
    public void testAttemptedSlf4jInterpolation() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "// BUG: Diagnostic contains: Do not use slf4j-style formatting in logsafe Exceptions",
                        "Exception foo = new SafeIllegalArgumentException(\"Foo {}\", SafeArg.of(\"foo\", 1));",
                        "}")
                .doTest();
    }

    @Test
    public void testAttemptedSlf4jInterpolationWithCause() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "// BUG: Diagnostic contains: Do not use slf4j-style formatting in logsafe Exceptions",
                        "Exception foo = new SafeIllegalArgumentException(\"Foo {}\",",
                        "new RuntimeException(), SafeArg.of(\"foo\", 1));",
                        "}")
                .doTest();
    }

    @Test
    public void testAttemptedSlf4jInterpolationNoArgs() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "class Test {",
                        "// BUG: Diagnostic contains: Do not use slf4j-style formatting in logsafe Exceptions",
                        "Exception foo = new SafeIllegalArgumentException(\"Foo {}\");",
                        "}")
                .doTest();
    }

    @Test
    public void testNegativeWithArg() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "Exception foo = new SafeIllegalArgumentException(\"Foo\", SafeArg.of(\"foo\", 1));",
                        "}")
                .doTest();
    }

    @Test
    public void testNegativeNoArgs() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "class Test {",
                        "Exception foo = new SafeIllegalArgumentException();",
                        "}")
                .doTest();
    }
}
