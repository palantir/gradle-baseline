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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class Slf4jThrowableTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(RuntimeException t) {",
                        "    log.trace(\"message\", \"first\", \"second\", t, \"third\");",
                        "    log.debug(\"message\", t, \"first\", \"second\", \"third\");",
                        "    log.info(\"message\", t);",
                        "    log.warn(\"message\", \"first\", t, \"second\");",
                        "    log.error(\"message\", t, \"arg\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(RuntimeException t) {",
                        "    log.trace(\"message\", \"first\", \"second\", \"third\", t);",
                        "    log.debug(\"message\", \"first\", \"second\", \"third\", t);",
                        "    log.info(\"message\", t);",
                        "    log.warn(\"message\", \"first\", \"second\", t);",
                        "    log.error(\"message\", \"arg\", t);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testMultipleExceptionsNotFixed() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(Throwable one, Exception two) {",
                        "    log.warn(\"message\", one, two);",
                        "    log.error(\"message\",  \"arg\", one, two);",
                        "  }",
                        "}")
                .expectUnchanged()
                // This should fail validation, but no fixes should be attempted
                .doTestExpectingFailure(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    RefactoringValidator fix() {
        return RefactoringValidator.of(new Slf4jThrowable(), getClass());
    }
}
