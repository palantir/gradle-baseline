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

class LoggerEnclosingClassTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    private static final Logger log = LoggerFactory.getLogger(String.class);",
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
    void testFix_generic() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test<T> {",
                        "    private static final Logger log = LoggerFactory.getLogger(String.class);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test<T> {",
                        "    private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "}")
                .doTest();
    }

    @Test
    void testFix_interface() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "interface Test {",
                        "    Logger log = LoggerFactory.getLogger(String.class);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "interface Test {",
                        "    Logger log = LoggerFactory.getLogger(Test.class);",
                        "}")
                .doTest();
    }

    @Test
    void testFix_nested() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    interface Nested {",
                        "        Logger log = LoggerFactory.getLogger(Test.class);",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    interface Nested {",
                        "        Logger log = LoggerFactory.getLogger(Nested.class);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testFix_anonymous() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    Runnable run = new Runnable() {",
                        "        private final Logger log = LoggerFactory.getLogger(String.class);",
                        "        @Override public void run() {}",
                        "    };",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "    Runnable run = new Runnable() {",
                        "        private final Logger log = LoggerFactory.getLogger(Test.class);",
                        "        @Override public void run() {}",
                        "    };",
                        "}")
                .doTest();
    }

    @Test
    void testNegative() {
        fix().addInputLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        // Not great, but it's out of scope for this check to validate dynamic cases.
                        "    private static final Logger log = LoggerFactory.getLogger(dynamic());",
                        "    private static Class<String> dynamic() {",
                        "        return String.class;",
                        "    }",
                        "    private static void func() {",
                        "        LoggerFactory.getLogger(String.class);",
                        "        Logger local = LoggerFactory.getLogger(String.class);",
                        "    }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new LoggerEnclosingClass(), getClass());
    }
}
