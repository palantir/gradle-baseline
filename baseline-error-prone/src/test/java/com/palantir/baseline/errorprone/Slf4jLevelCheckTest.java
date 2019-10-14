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

class Slf4jLevelCheckTest {

    @Test
    void testMessage() {
        helper()
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: level must match the most severe",
                        "    if (log.isInfoEnabled()) {",
                        "        log.warn(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectLevel() {
        helper()
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    if (log.isInfoEnabled()) {",
                        "        log.info(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFix_simple() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    if (log.isInfoEnabled()) {",
                        "        log.warn(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    if (log.isWarnEnabled()) {",
                        "        log.warn(\"foo\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_nestedConditional() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    if (log.isInfoEnabled()) {",
                        "        if (this.getClass().getName().startsWith(\"c\")) {",
                        "            log.info(\"foo\");",
                        "        } else {",
                        "            log.warn(\"bar\");",
                        "        }",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f() {",
                        "    if (log.isWarnEnabled()) {",
                        "        if (this.getClass().getName().startsWith(\"c\")) {",
                        "            log.info(\"foo\");",
                        "        } else {",
                        "            log.warn(\"bar\");",
                        "        }",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(Slf4jLevelCheck.class, getClass());
    }

    private BugCheckerRefactoringTestHelper fix() {
        return BugCheckerRefactoringTestHelper.newInstance(new Slf4jLevelCheck(), getClass());
    }
}
