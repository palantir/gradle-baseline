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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class PreferSafeLoggableExceptionsTest {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PreferSafeLoggableExceptions.class, getClass());
    }

    @Test
    public void illegal_argument_exception() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "class Bean {",
                "// BUG: Diagnostic contains: Prefer SafeIllegalArgumentException",
                "Exception foo = new IllegalArgumentException(\"Foo\");",
                "}").doTest();
    }

    @Test
    public void illegal_state_exception() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "class Bean {",
                "// BUG: Diagnostic contains: Prefer SafeIllegalStateException",
                "Exception foo = new IllegalStateException(\"Foo\");",
                "}").doTest();
    }

    @Test
    public void io_exception() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "class Bean {",
                "// BUG: Diagnostic contains: Prefer SafeIOException",
                "Exception foo = new java.io.IOException(\"Foo\");",
                "}").doTest();
    }
}
