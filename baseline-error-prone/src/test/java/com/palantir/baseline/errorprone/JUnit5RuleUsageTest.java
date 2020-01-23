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
public class JUnit5RuleUsageTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(JUnit5RuleUsage.class, getClass());
    }

    @Test
    public void test_rule_with_junit5() {
        compilationHelper
                .addSourceLines(
                        "TestCase.java",
                        "import org.junit.Rule;",
                        "import org.junit.jupiter.api.Test;",
                        "// BUG: Diagnostic contains: Do not use Rule/ClassRule",
                        "class TestCase {",
                        "@Rule public int foo = 1;",
                        "@Test",
                        "public void test() { }",
                        "}")
                .doTest();
    }

    @Test
    public void test_classrule_with_junit5() {
        compilationHelper
                .addSourceLines(
                        "TestCase.java",
                        "import org.junit.ClassRule;",
                        "import org.junit.jupiter.api.Test;",
                        "// BUG: Diagnostic contains: Do not use Rule/ClassRule",
                        "class TestCase {",
                        "@ClassRule public static int foo = 1;",
                        "@Test",
                        "public void test() { }",
                        "}")
                .doTest();
    }

    @Test
    public void test_rule_migration_support() {
        compilationHelper
                .addSourceLines(
                        "TestCase.java",
                        "import org.junit.Rule;",
                        "import org.junit.jupiter.api.Test;",
                        "import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;",
                        "@EnableRuleMigrationSupport",
                        "class TestCase {",
                        "@Rule public static int foo = 1;",
                        "@Test",
                        "public void test() { }",
                        "}")
                .doTest();
    }

    @Test
    public void test_rule_with_junit4() {
        compilationHelper
                .addSourceLines(
                        "TestCase.java",
                        "import org.junit.Rule;",
                        "import org.junit.Test;",
                        "class TestCase {",
                        "@Rule public static int foo = 1;",
                        "@Test",
                        "public void test() { }",
                        "}")
                .doTest();
    }
}
