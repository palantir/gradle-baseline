/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

class ConsistentLoggerNameTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static final Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "}")
                .doTest();
    }

    @Test
    void testFix_references() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static final Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "    private void foo() {",
                        "        LOG.error(\"error\");",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "    private void foo() {",
                        "        log.error(\"error\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void ignores_local_variables() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "}")
                .doTest();
    }

    @Test
    void ignores_non_final_methods() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static Logger LOG = LoggerFactory.getLogger(Test.class);",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ConsistentLoggerName(), getClass());
    }
}
