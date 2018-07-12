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
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class CatchBlockLogExceptionTest {

    private static final String errorMsg = "BUG: Diagnostic contains: "
            + "Catch block contains log statements but thrown exception is never logged";

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(CatchBlockLogException.class, getClass());
    }

    @Test
    public void testLogException() {
        test(IllegalArgumentException.class, "log.info(\"hello\", e);", Optional.empty());
        test(RuntimeException.class, "log.info(\"hello\", e);", Optional.empty());
        test(Exception.class, "log.info(\"hello\", e);", Optional.empty());
        test(Throwable.class, "log.info(\"hello\", e);", Optional.empty());
    }

    @Test
    public void testLogExceptionNotLastArg() {
        test(RuntimeException.class, "log.info(\"hello\", e, \"world\");", Optional.of(errorMsg));
    }

    @Test
    public void testNoLogException() {
        test(RuntimeException.class, "log.info(\"hello\");", Optional.of(errorMsg));
    }

    private void test(Class<? extends Throwable> exceptionClass, String catchStatement, Optional<String> error) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(String param) {",
                        "    try {",
                        "        log.info(\"hello\");",
                        "// " + error.orElse(""),
                        "    } catch (" + exceptionClass.getSimpleName() + " e) {",
                        "        " + catchStatement,
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }
}
