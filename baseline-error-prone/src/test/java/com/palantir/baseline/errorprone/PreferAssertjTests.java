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
    public void fix_booleanAsserts() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    Assert.assertFalse(b);",
                        "    Assert.assertFalse(\"desc\", b);",
                        "    Assert.assertTrue(b);",
                        "    Assert.assertTrue(\"desc\", b);",
                        "    assertThat(\"desc\", b);",
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
                        "    assertThat(b).describedAs(\"desc\").isFalse();",
                        "    assertThat(b).isTrue();",
                        "    assertThat(b).describedAs(\"desc\").isTrue();",
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
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo(boolean b) {",
                        "    assertThat(true).isEqualTo(false);",
                        "    assertThat(b).isFalse();",
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
                        "import static org.junit.Assert.assertNotNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertNull(s);",
                        "    assertNull(\"desc\", s);",
                        "    assertNotNull(s);",
                        "    assertNotNull(\"desc\", s);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotNull;",
                        "import static org.junit.Assert.assertNull;",
                        "class Test {",
                        "  void foo(String s) {",
                        "    assertThat(s).isNull();",
                        "    assertThat(s).describedAs(\"desc\").isNull();",
                        "    assertThat(s).isNotNull();",
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
                        "import static org.junit.Assert.assertNotSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertSame(a, b);",
                        "    assertSame(\"desc\", a, b);",
                        "    assertNotSame(a, b);",
                        "    assertNotSame(\"desc\", a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotSame;",
                        "import static org.junit.Assert.assertSame;",
                        "class Test {",
                        "  void foo(String a, String b) {",
                        "    assertThat(b).isSameAs(a);",
                        "    assertThat(b).describedAs(\"desc\").isSameAs(a);",
                        "    assertThat(b).isNotSameAs(a);",
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
                        "",
                        "import org.junit.Assert;",
                        "class Test {",
                        "  void foo() {",
                        "    fail();",
                        "    fail(\"desc\");",
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
                        "    fail(\"fail\");",
                        "    fail(\"desc\");",
                        "    fail(\"desc\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertEqualsFloating() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertEquals;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float fl, double db) {",
                        "    assertEquals(.1f, fl, .01f);",
                        "    assertEquals(\"desc\", .1f, fl, .01f);",
                        "    assertEquals(.1D, db, .01D);",
                        "    assertEquals(\"desc\", .1D, db, .01D);",
                        "    assertEquals(\"desc\", .1D, db, 0);",
                        "    assertEquals(.1D, db, 0D);",
                        "    assertNotEquals(.1f, fl, .01f);",
                        "    assertNotEquals(\"desc\", .1f, fl, .01f);",
                        "    assertNotEquals(.1D, db, .01D);",
                        "    assertNotEquals(\"desc\", .1D, db, .01D);",
                        "    assertNotEquals(\"desc\", .1D, db, 0.0);",
                        "    assertNotEquals(.1D, db, 0D);",
                        "    assertEquals(1D, db, 1);",
                        "    assertEquals(1, db, 1D);",
                        "    assertEquals(1D, db, 1f);",
                        "    assertNotEquals(\"desc\", 1f, db, 1D);",
                        "    assertEquals(1f, fl, 1);",
                        "    assertNotEquals(1, fl, 1f);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.assertj.core.api.Assertions.within;",
                        "import static org.junit.Assert.assertEquals;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(float fl, double db) {",
                        "    assertThat(fl).isCloseTo(.1f, within(.01f));",
                        "    assertThat(fl).describedAs(\"desc\").isCloseTo(.1f, within(.01f));",
                        "    assertThat(db).isCloseTo(.1D, within(.01D));",
                        "    assertThat(db).describedAs(\"desc\").isCloseTo(.1D, within(.01D));",
                        "    assertThat(db).describedAs(\"desc\").isEqualTo(.1D);",
                        "    assertThat(db).isEqualTo(.1D);",
                        "    assertThat(fl).isNotCloseTo(.1f, within(.01f));",
                        "    assertThat(fl).describedAs(\"desc\").isNotCloseTo(.1f, within(.01f));",
                        "    assertThat(db).isNotCloseTo(.1D, within(.01D));",
                        "    assertThat(db).describedAs(\"desc\").isNotCloseTo(.1D, within(.01D));",
                        "    assertThat(db).describedAs(\"desc\").isNotEqualTo(.1D);",
                        "    assertThat(db).isNotEqualTo(.1D);",
                        "    assertThat(db).isCloseTo(1D, within((double) 1));",
                        "    assertThat(db).isCloseTo((double) 1, within(1D));",
                        "    assertThat(db).isCloseTo(1D, within((double) 1f));",
                        "    assertThat(db).describedAs(\"desc\").isNotCloseTo((double) 1f, within(1D));",
                        "    assertThat(fl).isCloseTo(1f, within((float) 1));",
                        "    assertThat(fl).isNotCloseTo((float) 1, within(1f));",
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
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertEquals(\"1\", value);",
                        "    assertEquals(\"desc\", \"1\", value);",
                        "    assertNotEquals(\"1\", value);",
                        "    assertNotEquals(\"desc\", \"1\", value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertEquals;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"1\");",
                        "    assertThat(value).describedAs(\"desc\").isEqualTo(\"1\");",
                        "    assertThat(value).isNotEqualTo(\"1\");",
                        "    assertThat(value).describedAs(\"desc\").isNotEqualTo(\"1\");",
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
                "    // BUG: Diagnostic contains: Prefer AssertJ",
                "    assertArrayEquals(\"desc\", param, new float[] { 1f }, .1f);",
                "  }",
                "}")
                .doTest();
    }

    @Test
    public void fix_assertArrayEqualsDelta() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(float[] floatArray, double[] doubleArray) {",
                        "    assertArrayEquals(\"desc\", new float[] { 1f }, floatArray, 0);",
                        "    assertArrayEquals(new float[] { 1f }, floatArray, 0);",
                        "    assertArrayEquals(\"desc\", new double[] { 1D }, doubleArray, 0);",
                        "    assertArrayEquals(new double[] { 1D }, doubleArray, 0);",
                        // nonzero delta doesn't have a migration path
                        "    assertArrayEquals(\"desc\", new float[] { 1f }, floatArray, .1f);",
                        "    assertArrayEquals(\"desc\", new double[] { 1D }, doubleArray, .1D);",
                        "    assertArrayEquals(new float[] { 1f }, floatArray, .1f);",
                        "    assertArrayEquals(new double[] { 1D }, doubleArray, .1D);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertArrayEquals;",
                        "class Test {",
                        "  void foo(float[] floatArray, double[] doubleArray) {",
                        "    assertThat(floatArray).describedAs(\"desc\").isEqualTo(new float[] { 1f });",
                        "    assertThat(floatArray).isEqualTo(new float[] { 1f });",
                        "    assertThat(doubleArray).describedAs(\"desc\").isEqualTo(new double[] { 1D });",
                        "    assertThat(doubleArray).isEqualTo(new double[] { 1D });",
                        "    assertArrayEquals(\"desc\", new float[] { 1f }, floatArray, .1f);",
                        "    assertArrayEquals(\"desc\", new double[] { 1D }, doubleArray, .1D);",
                        "    assertArrayEquals(new float[] { 1f }, floatArray, .1f);",
                        "    assertArrayEquals(new double[] { 1D }, doubleArray, .1D);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertNotEqualsInt() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertNotEquals(1, value);",
                        "    assertNotEquals(\"desc\", 1, value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.junit.Assert.assertNotEquals;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).isNotEqualTo(1);",
                        "    assertThat(value).describedAs(\"desc\").isNotEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThatInt() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.junit.Assert.assertThat;",
                        "import static org.hamcrest.Matchers.is;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value, is(1));",
                        "    assertThat(\"desc\", value, is(1));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.is;",
                        "class Test {",
                        "  void foo(int value) {",
                        "    assertThat(value).isEqualTo(1);",
                        "    assertThat(value).describedAs(\"desc\").isEqualTo(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_matcherAssertThatString_fallback() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, hasToString(\"str\"));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "",
                        "import org.assertj.core.api.HamcrestCondition;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).is(new HamcrestCondition<>(hasToString(\"str\")));",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_is_instanceOf() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, is(instanceOf(String.class)));",
                        "    assertThat(value, instanceOf(String.class));",
                        "    assertThat(value, is(not(instanceOf(String.class))));",
                        "    assertThat(value, not(instanceOf(String.class)));",
                        "    assertThat(\"desc\", value, is(instanceOf(String.class)));",
                        "    assertThat(\"desc\", value, instanceOf(String.class));",
                        "    assertThat(\"desc\", value, is(not(instanceOf(String.class))));",
                        "    assertThat(\"desc\", value, not(instanceOf(String.class)));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isInstanceOf(String.class);",
                        "    assertThat(value).isInstanceOf(String.class);",
                        "    assertThat(value).isNotInstanceOf(String.class);",
                        "    assertThat(value).isNotInstanceOf(String.class);",
                        "    assertThat(value).describedAs(\"desc\").isInstanceOf(String.class);",
                        "    assertThat(value).describedAs(\"desc\").isInstanceOf(String.class);",
                        "    assertThat(value).describedAs(\"desc\").isNotInstanceOf(String.class);",
                        "    assertThat(value).describedAs(\"desc\").isNotInstanceOf(String.class);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_equality() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, is(\"str\"));",
                        "    assertThat(value, equalTo(\"str\"));",
                        "    assertThat(value, is(equalTo(\"str\")));",
                        "    assertThat(value, org.hamcrest.CoreMatchers.equalToObject(\"str\"));",
                        "    assertThat(value, not(not(equalTo(\"str\"))));",
                        "    assertThat(value, not(is(\"str\")));",
                        "    assertThat(value, not(equalTo(\"str\")));",
                        "    assertThat(value, is(not(equalTo(\"str\"))));",
                        "    assertThat(value, not(org.hamcrest.CoreMatchers.equalToObject(\"str\")));",
                        "    assertThat(value, not(not(not(equalTo(\"str\")))));",
                        "    assertThat(\"desc\", value, not(not(not(equalTo(\"str\")))));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).describedAs(\"desc\").isNotEqualTo(\"str\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_nullness() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, is(not(is(notNullValue(String.class)))));",
                        "    assertThat(value, is(not(is(nullValue(String.class)))));",
                        "    assertThat(value, is(nullValue(String.class)));",
                        "    assertThat(value, nullValue());",
                        "    assertThat(value, is(notNullValue(String.class)));",
                        "    assertThat(value, notNullValue());",
                        "    assertThat(\"desc\", value, is(not(is(notNullValue(String.class)))));",
                        "    assertThat(\"desc\", value, is(not(is(nullValue(String.class)))));",
                        "    assertThat(\"desc\", value, is(nullValue(String.class)));",
                        "    assertThat(\"desc\", value, nullValue());",
                        "    assertThat(\"desc\", value, is(notNullValue(String.class)));",
                        "    assertThat(\"desc\", value, notNullValue());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isNull();",
                        "    assertThat(value).isNotNull();",
                        "    assertThat(value).isNull();",
                        "    assertThat(value).isNull();",
                        "    assertThat(value).isNotNull();",
                        "    assertThat(value).isNotNull();",
                        "    assertThat(value).describedAs(\"desc\").isNull();",
                        "    assertThat(value).describedAs(\"desc\").isNotNull();",
                        "    assertThat(value).describedAs(\"desc\").isNull();",
                        "    assertThat(value).describedAs(\"desc\").isNull();",
                        "    assertThat(value).describedAs(\"desc\").isNotNull();",
                        "    assertThat(value).describedAs(\"desc\").isNotNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_contains() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(Iterable<String> value, String[] arrayValue) {",
                        "    assertThat(value, hasItem(\"str\"));",
                        "    assertThat(arrayValue, hasItemInArray(\"str\"));",
                        "    assertThat(value, hasItems(\"one\", \"two\"));",
                        "    assertThat(arrayValue, arrayContainingInAnyOrder(\"one\", \"two\"));",
                        "    assertThat(value, containsInAnyOrder(\"one\", \"two\"));",
                        "    assertThat(value, contains(\"one\", \"two\"));",
                        "    assertThat(value, not(hasItem(\"str\")));",
                        "    assertThat(arrayValue, not(hasItemInArray(\"str\")));",
                        "    assertThat(arrayValue[0], containsString(\"str\"));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(Iterable<String> value, String[] arrayValue) {",
                        "    assertThat(value).contains(\"str\");",
                        "    assertThat(arrayValue).contains(\"str\");",
                        "    assertThat(value).contains(\"one\", \"two\");",
                        "    assertThat(arrayValue).containsExactlyInAnyOrder(\"one\", \"two\");",
                        "    assertThat(value).containsExactlyInAnyOrder(\"one\", \"two\");",
                        "    assertThat(value).containsExactly(\"one\", \"two\");",
                        "    assertThat(value).doesNotContain(\"str\");",
                        "    assertThat(arrayValue).doesNotContain(\"str\");",
                        "    assertThat(arrayValue[0]).contains(\"str\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_empty() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "",
                        "import java.util.*;",
                        "class Test {",
                        "  void foo(Iterable<String> it, String[] ar, List<String> li) {",
                        "    assertThat(li, empty());",
                        "    assertThat(li, emptyIterable());",
                        "    assertThat(li, emptyCollectionOf(String.class));",
                        "    assertThat(it, emptyIterable());",
                        "    assertThat(it, emptyIterableOf(String.class));",
                        "    assertThat(ar, emptyArray());",
                        "    assertThat(li, is(not(empty())));",
                        "    assertThat(li, not(emptyIterable()));",
                        "    assertThat(li, not(emptyCollectionOf(String.class)));",
                        "    assertThat(it, not(emptyIterable()));",
                        "    assertThat(it, not(emptyIterableOf(String.class)));",
                        "    assertThat(ar, not(emptyArray()));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "",
                        "import java.util.*;",
                        "class Test {",
                        "  void foo(Iterable<String> it, String[] ar, List<String> li) {",
                        "    assertThat(li).isEmpty();",
                        "    assertThat(li).isEmpty();",
                        "    assertThat(li).isEmpty();",
                        "    assertThat(it).isEmpty();",
                        "    assertThat(it).isEmpty();",
                        "    assertThat(ar).isEmpty();",
                        "    assertThat(li).isNotEmpty();",
                        "    assertThat(li).isNotEmpty();",
                        "    assertThat(li).isNotEmpty();",
                        "    assertThat(it).isNotEmpty();",
                        "    assertThat(it).isNotEmpty();",
                        "    assertThat(ar).isNotEmpty();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_size() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "",
                        "import java.util.*;",
                        "class Test {",
                        "  void foo(Iterable<String> it, String[] ar, List<String> li) {",
                        "    assertThat(li, hasSize(3));",
                        "    assertThat(li, iterableWithSize(3));",
                        "    assertThat(it, iterableWithSize(3));",
                        "    assertThat(ar, arrayWithSize(3));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "",
                        "import java.util.*;",
                        "class Test {",
                        "  void foo(Iterable<String> it, String[] ar, List<String> li) {",
                        "    assertThat(li).hasSize(3);",
                        "    assertThat(li).hasSize(3);",
                        "    assertThat(it).hasSize(3);",
                        "    assertThat(ar).hasSize(3);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_same() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(Object a, Object b) {",
                        "    assertThat(a, theInstance(b));",
                        "    assertThat(a, sameInstance(b));",
                        "    assertThat(a, is(not(theInstance(b))));",
                        "    assertThat(a, not(sameInstance(b)));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(Object a, Object b) {",
                        "    assertThat(a).isSameAs(b);",
                        "    assertThat(a).isSameAs(b);",
                        "    assertThat(a).isNotSameAs(b);",
                        "    assertThat(a).isNotSameAs(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_strings() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, containsString(\"str\"));",
                        "    assertThat(value, not(containsString(\"str\")));",
                        "    assertThat(value, startsWith(\"str\"));",
                        "    assertThat(value, not(startsWith(\"str\")));",
                        "    assertThat(value, endsWith(\"str\"));",
                        "    assertThat(value, not(endsWith(\"str\")));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).contains(\"str\");",
                        "    assertThat(value).doesNotContain(\"str\");",
                        "    assertThat(value).startsWith(\"str\");",
                        "    assertThat(value).doesNotStartWith(\"str\");",
                        "    assertThat(value).endsWith(\"str\");",
                        "    assertThat(value).doesNotEndWith(\"str\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assertThat_wrongImport() {
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.core.Is.is;",
                        "import static org.hamcrest.core.IsEqual.equalTo;",
                        "import static org.hamcrest.core.IsNot.not;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value, is(\"str\"));",
                        "    assertThat(value, equalTo(\"str\"));",
                        "    assertThat(value, not(is(\"str\")));",
                        "    assertThat(value, not(equalTo(\"str\")));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.core.Is.is;",
                        "import static org.hamcrest.core.IsEqual.equalTo;",
                        "import static org.hamcrest.core.IsNot.not;",
                        "class Test {",
                        "  void foo(String value) {",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "    assertThat(value).isNotEqualTo(\"str\");",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void fix_assert_iterableMap() {
        // Iterable Maps result in ambiguous assertThat references
        // to to both assertThat(Iterable) and assertThat(Map)
        test()
                .addInputLines(
                        "Test.java",
                        "import static org.hamcrest.MatcherAssert.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "import static org.junit.Assert.assertEquals;",
                        "import static org.junit.Assert.assertNull;",
                        "",
                        "import java.util.Map;",
                        "class Test {",
                        "  interface IMap<K, V> extends Map<K, V>, Iterable<Map.Entry<K, V>> {}",
                        "  <K, V> void foo(IMap<K, V> expected, IMap<K, V> actual) {",
                        "    assertThat(actual, equalTo(expected));",
                        "    assertEquals(expected, actual);",
                        "    assertEquals(\"desc\", expected, actual);",
                        "    assertNull(actual);",
                        "    assertNull(\"desc\", actual);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import static org.hamcrest.Matchers.*;",
                        "import static org.junit.Assert.assertEquals;",
                        "import static org.junit.Assert.assertNull;",
                        "",
                        "import java.util.Map;",
                        "class Test {",
                        "  interface IMap<K, V> extends Map<K, V>, Iterable<Map.Entry<K, V>> {}",
                        "  <K, V> void foo(IMap<K, V> expected, IMap<K, V> actual) {",
                        "    assertThat((Map<?, ?>) actual).isEqualTo(expected);",
                        "    assertThat((Map<?, ?>) actual).isEqualTo(expected);",
                        "    assertThat((Map<?, ?>) actual).describedAs(\"desc\").isEqualTo(expected);",
                        "    assertThat((Map<?, ?>) actual).isNull();",
                        "    assertThat((Map<?, ?>) actual).describedAs(\"desc\").isNull();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private BugCheckerRefactoringTestHelper test() {
        return BugCheckerRefactoringTestHelper.newInstance(new PreferAssertj(), getClass());
    }
}
