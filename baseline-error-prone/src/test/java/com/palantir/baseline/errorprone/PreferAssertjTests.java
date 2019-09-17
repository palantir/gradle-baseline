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
import org.junit.Test;

@SuppressWarnings("RegexpSinglelineJava") // Testing against assertions that aren't allowed
public class PreferAssertjTests {

    @Test
    public void fix_assertFalse() {
        test()
                .addInputLines(
                        "Test.java",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    Assert.assertFalse(b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(b).isFalse();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertFalseDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    Assert.assertFalse(\"desc\", b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(b).describedAs(\"desc\").isFalse();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertTrue() {
        test()
                .addInputLines(
                        "Test.java",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    Assert.assertTrue(b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(b).isTrue();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertTrueDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    Assert.assertTrue(\"desc\", b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(b).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fixAssertFalse_existingAssertThat() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(true, org.hamcrest.CoreMatchers.equalTo(false));",
                        "    Assert.assertFalse(b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertThat;",
                        "",
                        "import org.assertj.core.api.Assertions;",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(true, org.hamcrest.CoreMatchers.equalTo(false));",
                        "    Assertions.assertThat(b).isFalse();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNull() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertNull(s);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertThat(s).isNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNullDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertNull(\"desc\", s);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertThat(s).describedAs(\"desc\").isNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotNull() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertNotNull(s);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertThat(s).isNotNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotNullDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertNotNull(\"desc\", s);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertThat(s).describedAs(\"desc\").isNotNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertSame() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertSame(a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertThat(b).isSameAs(a);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertSameDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertSame(\"desc\", a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertThat(b).describedAs(\"desc\").isSameAs(a);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotSame() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertSame(a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertThat(b).isSameAs(a);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotSameDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertNotSame(\"desc\", a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertThat(b).describedAs(\"desc\").isNotSameAs(a);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_fail() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.fail;",
                        "class Test {",
                        "  void foo() {",
                        "    fail();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.fail;",
                        "class Test {",
                        "  void foo() {",
                        "    fail(\"fail\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_failDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.fail;",
                        "class Test {",
                        "  void foo() {",
                        "    fail(\"desc\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.fail;",
                        "class Test {",
                        "  void foo() {",
                        "    fail(\"desc\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_failDescriptionNonStaticImport() {
        test()
                .addInputLines(
                        "Test.java",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo() {",
                        "    Assert.fail(\"desc\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.fail;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo() {",
                        "    fail(\"desc\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsFloat() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertEquals(.1f, value, .01f);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertThat(value).isCloseTo(.1f, within(.01f));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsFloatDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertEquals(\"desc\", .1f, value, .01f);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertThat(value).describedAs(\"desc\").isCloseTo(.1f, within(.01f));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsDouble() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertEquals(.1D, value, .01D);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertThat(value).isCloseTo(.1D, within(.01D));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsDoubleDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertEquals(\"desc\", .1D, value, .01D);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertThat(value).describedAs(\"desc\").isCloseTo(.1D, within(.01D));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsFloat() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertNotEquals(.1f, value, .01f);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertThat(value).isNotCloseTo(.1f, within(.01f));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsFloatDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertNotEquals(\"desc\", .1f, value, .01f);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float value) {",
                        "    assertThat(value).describedAs(\"desc\").isNotCloseTo(.1f, within(.01f));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsDouble() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertNotEquals(.1D, value, .01D);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertThat(value).isNotCloseTo(.1D, within(.01D));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsDoubleDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertNotEquals(\"desc\", .1D, value, .01D);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(double value) {",
                        "    assertThat(value).describedAs(\"desc\").isNotCloseTo(.1D, within(.01D));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsInt() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertEquals(1, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).isEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsString() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertEquals(\"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsString_testcase() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static junit.framework.TestCase.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertEquals(\"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static junit.framework.TestCase.assertEquals;",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsString_frameworkAssert() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static junit.framework.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertEquals(\"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static junit.framework.Assert.assertEquals;",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsIntDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertEquals(\"desc\", 1, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).describedAs(\"desc\").isEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsStringDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertEquals(\"desc\", \"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).describedAs(\"desc\").isEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertArrayEqualsInt() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(int[] value) {",
                        "    assertArrayEquals(new int[] { 1 }, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(int[] value) {",
                        "    assertThat(value).isEqualTo(new int[] { 1 });",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertArrayEqualsIntDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(int[] value) {",
                        "    assertArrayEquals(\"desc\", new int[] { 1 }, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(int[] value) {",
                        "    assertThat(value).describedAs(\"desc\").isEqualTo(new int[] { 1 });",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fails_assertArrayEqualsDelta_double() {
        CompilationTestHelper.newInstance(PreferAssertj.class, getClass()).addSourceLines(
                "Test.java",
                "import static org.junit.Assert.assertArrayEquals;",
                "class Test {",
                "  void f(double[] param) {",
                "    // BUG: Diagnostic contains: Prefer AssertJ",
                "    assertArrayEquals(param, new double[] { 1D }, .1D);",
                "  }",
                "}")
                .doTest();
    }

    @Test
    public void fails_assertArrayEqualsDeltaDescription_double() {
        CompilationTestHelper.newInstance(PreferAssertj.class, getClass()).addSourceLines(
                "Test.java",
                "import static org.junit.Assert.assertArrayEquals;",
                "class Test {",
                "  void f(double[] param) {",
                "    // BUG: Diagnostic contains: Prefer AssertJ",
                "    assertArrayEquals(\"desc\", param, new double[] { 1D }, .1D);",
                "  }",
                "}")
                .doTest();
    }

    @Test
    public void fails_assertArrayEqualsDelta_float() {
        CompilationTestHelper.newInstance(PreferAssertj.class, getClass()).addSourceLines(
                "Test.java",
                "import static org.junit.Assert.assertArrayEquals;",
                "class Test {",
                "  void f(float[] param) {",
                "    // BUG: Diagnostic contains: Prefer AssertJ",
                "    assertArrayEquals(param, new float[] { 1f }, .1f);",
                "  }",
                "}")
                .doTest();
    }

    @Test
    public void fails_assertArrayEqualsDeltaDescription_float() {
        CompilationTestHelper.newInstance(PreferAssertj.class, getClass()).addSourceLines(
                "Test.java",
                "import static org.junit.Assert.assertArrayEquals;",
                "class Test {",
                "  void f(float[] param) {",
                "    // BUG: Diagnostic contains: Prefer AssertJ",
                "    assertArrayEquals(\"desc\", param, new float[] { 1f }, .1f);",
                "  }",
                "}")
                .doTest();
    }

    // assertNotEquals

    @Test
    public void fix_assertNotEqualsInt() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertNotEquals(1, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).isNotEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsString() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertNotEquals(\"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isNotEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsIntDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertNotEquals(\"desc\", 1, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).describedAs(\"desc\").isNotEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsStringDescription() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertNotEquals(\"desc\", \"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).describedAs(\"desc\").isNotEqualTo(\"1\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private BugCheckerRefactoringTestHelper test() {
        return BugCheckerRefactoringTestHelper.newInstance(new PreferAssertj(), getClass());
    }
}
