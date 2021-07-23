/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

class PreferSafeLoggerTest {

    private RefactoringValidator fix() {
        return RefactoringValidator.of(PreferSafeLogger.class, getClass());
    }

    @Test
    void testSimpleFix() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSimpleFix_stringAccessor() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(\"str\");",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(\"str\");",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSimpleFixWithThrowable() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action(Throwable t) {",
                        "    log.info(\"foo\", t);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(Test.class);",
                        "  void action(Throwable t) {",
                        "    log.info(\"foo\", t);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSimpleFixWithArgAndThrowable() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action(Throwable t) {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"), t);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(Test.class);",
                        "  void action(Throwable t) {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"), t);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFixWithLevelCheck() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action(Throwable t) {",
                        "    if (log.isInfoEnabled()) {",
                        "        log.info(\"foo\", t);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(Test.class);",
                        "  void action(Throwable t) {",
                        "    if (log.isInfoEnabled()) {",
                        "        log.info(\"foo\", t);",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresIncorrectlyOrderedThrowables() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action(Throwable t) {",
                        "    log.info(\"foo\", t, SafeArg.of(\"name\", \"value\"));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testUnsafeLoggingUnmodified() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", \"value\");",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testPassedLoggerUnmodified() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", \"value\"));",
                        "    bh(log);",
                        "  }",
                        "  static Logger bh(Logger log) {",
                        "    return log;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testGetNameInArgUnmodified() {
        // getName is not supported by SafeLogger, so we shouldn't make changes that won't compile.
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", log.getName()));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIsTraceEnabledInArg() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", log.isTraceEnabled()));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.logsafe.logger.SafeLogger;",
                        "import com.palantir.logsafe.logger.SafeLoggerFactory;",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  private static final SafeLogger log = SafeLoggerFactory.get(Test.class);",
                        "  void action() {",
                        "    log.info(\"foo\", SafeArg.of(\"name\", log.isTraceEnabled()));",
                        "  }",
                        "}")
                .doTest();
    }
}
