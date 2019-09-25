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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DangerousThrowableMessageSafeArgTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousThrowableMessageSafeArg.class, getClass());
    }

    @Test
    public void unsafe_safearg_value() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import " + SafeIllegalArgumentException.class.getName() + ';',
                "import " + SafeArg.class.getName() + ';',
                "class Bean {",
                "  public SafeArg<?> foo() {",
                "    Exception foo = new SafeIllegalArgumentException(\"Foo\");",
                "    // BUG: Diagnostic contains: Do not use throwable messages as SafeArg values",
                "    return SafeArg.of(\"foo\", foo.getMessage());",
                "  }",
                "}").doTest();
    }

    @Test
    public void safe_safearg_value() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import " + SafeIllegalArgumentException.class.getName() + ';',
                "import " + SafeArg.class.getName() + ';',
                "class Bean {",
                "  public SafeArg<?> foo() {",
                "    SafeIllegalArgumentException foo = new SafeIllegalArgumentException(\"Foo\");",
                "    return SafeArg.of(\"foo\", foo.getLogMessage());",
                "  }",
                "}").doTest();
    }

}
