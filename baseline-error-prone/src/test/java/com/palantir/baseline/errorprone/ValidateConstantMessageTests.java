/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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
import org.junit.Before;
import org.junit.Test;

public final class ValidateConstantMessageTests {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(ValidateConstantMessage.class, getClass());
    }

    private void test(String call) throws Exception {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.apache.commons.lang3.Validate;",
                        "import java.math.BigDecimal;",
                        "import java.util.Collection;",
                        "import java.util.Map;",
                        "class Test {",
                        "  void f(String param, boolean bArg, int iArg, Object oArg, Integer[] arrayArg, "
                                + "Collection<String> collectionArg, Map<String, String> mapArg, String stringArg, "
                                + "Iterable<String> iterableArg, double dArg) {",
                        "    // BUG: Diagnostic contains: non-constant message",
                        "    " + call,
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void positive() throws Exception {
        test("Validate.isTrue(param != \"string\", String.format(\"constant %s\", param));");
        test("Validate.isTrue(param != \"string\", \"constant\" + param);");
        test("Validate.isTrue(param != \"string\", \"constant\" + param, 0.0);");
        test("Validate.isTrue(param != \"string\", \"constant\" + param, 123L);");

        test("Validate.notNull(param, \"constant\" + param);");

        test("Validate.notEmpty(collectionArg, \"constant\" + param);");
        test("Validate.notEmpty(arrayArg, \"constant\" + param);");
        test("Validate.notEmpty(mapArg, \"constant\" + param);");
        test("Validate.notEmpty(stringArg, \"constant\" + param);");

        test("Validate.notBlank(stringArg, \"constant\" + param);");

        test("Validate.noNullElements(arrayArg, \"constant\" + param);");
        test("Validate.noNullElements(iterableArg, \"constant\" + param);");

        test("Validate.validIndex(arrayArg, 1, \"constant\" + param);");
        test("Validate.validIndex(collectionArg, 1, \"constant\" + param);");
        test("Validate.validIndex(stringArg, 1, \"constant\" + param);");

        test("Validate.validState(bArg, \"constant\" + param);");

        test("Validate.matchesPattern(stringArg, \"[A-Z]+\", \"constant\" + param);");

        test("Validate.notNaN(dArg, \"constant\" + param);");
        test("Validate.finite(dArg, \"constant\" + param);");

        test("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param);");
        test("Validate.inclusiveBetween(0L, 100L, 50L, \"constant\" + param);");
        test("Validate.inclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param);");

        test("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param);");
        test("Validate.exclusiveBetween(0L, 100L, 50L, \"constant\" + param);");
        test("Validate.exclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param);");

        test("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE, \"constant\" + param);");
        test("Validate.isAssignableFrom(Object.class, BigDecimal.class, "
                + "\"constant\" + param);");

        test("org.apache.commons.lang.Validate.isTrue(bArg, \"message\" + param);");
        test("org.apache.commons.lang.Validate.notNull(stringArg, \"message\" + param);");
        test("org.apache.commons.lang.Validate.notEmpty(arrayArg, \"message\" + param);");
        test("org.apache.commons.lang.Validate.noNullElements(arrayArg, \"message\" + param);");
        test("org.apache.commons.lang.Validate.allElementsOfType(collectionArg, Object.class, \"message\" + param);");
    }

    @Test
    public void negative() throws Exception {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.apache.commons.lang3.Validate;",
                        "import java.math.BigDecimal;",
                        "import java.util.Collection;",
                        "import java.util.Iterator;",
                        "import java.util.Map;",
                        "class Test {",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  void f(boolean bArg, int iArg, Object oArg, Integer[] arrayArg, "
                                + "Collection<String> collectionArg, Map<String, String> mapArg, String stringArg, "
                                + "Iterable<String> iterableArg, double dArg) {",

                        "    Validate.isTrue(bArg);",
                        "    Validate.isTrue(bArg, \"message %d\", 123L);",
                        "    Validate.isTrue(bArg, \"message %f\", 0.0);",
                        "    Validate.isTrue(bArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notNull(oArg);",
                        "    Validate.notNull(oArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notEmpty(arrayArg);",
                        "    Validate.notEmpty(arrayArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notEmpty(collectionArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notEmpty(mapArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notEmpty(stringArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notBlank(stringArg);",
                        "    Validate.notBlank(stringArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.noNullElements(arrayArg);",
                        "    Validate.noNullElements(arrayArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.noNullElements(iterableArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.validState(bArg);",
                        "    Validate.validState(bArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.notNaN(dArg);",
                        "    Validate.notNaN(dArg, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.finite(dArg);",
                        "    Validate.finite(dArg, \"message %s %s\", \"msg\", stringArg);",

                        // methods where the message template is the third arg
                        "    Validate.validIndex(arrayArg, 1);",
                        "    Validate.validIndex(arrayArg, 1, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.validIndex(collectionArg, 1, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.validIndex(stringArg, 1, \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.matchesPattern(stringArg, \"[A-Z]+\");",
                        "    Validate.matchesPattern(stringArg, \"[A-Z]+\", \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE);",
                        "    Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE,"
                                + " \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.isAssignableFrom(Object.class, BigDecimal.class);",
                        "    Validate.isAssignableFrom(Object.class, BigDecimal.class,"
                                + " \"message %s %s\", \"msg\", stringArg);",

                        // methods where the message template is the fourth arg
                        "    Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE);",
                        "    Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE,"
                                + " \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.inclusiveBetween(0L, 100L, 50L, \"message\");",
                        "    Validate.inclusiveBetween(0.0, 1.0, 0.5, \"message\");",
                        "    Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE);",
                        "    Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE,"
                                + " \"message %s %s\", \"msg\", stringArg);",
                        "    Validate.exclusiveBetween(0L, 100L, 50L, \"message\");",
                        "    Validate.exclusiveBetween(0.0, 1.0, 0.5, \"message\");",

                        // commons-lang 2.x methods
                        "    org.apache.commons.lang.Validate.isTrue(bArg);",
                        "    org.apache.commons.lang.Validate.isTrue(bArg, \"message\", stringArg);",
                        "    org.apache.commons.lang.Validate.notNull(stringArg);",
                        "    org.apache.commons.lang.Validate.notNull(stringArg, \"message\");",
                        "    org.apache.commons.lang.Validate.notEmpty(arrayArg);",
                        "    org.apache.commons.lang.Validate.notEmpty(arrayArg, \"message\");",
                        "    org.apache.commons.lang.Validate.noNullElements(arrayArg);",
                        "    org.apache.commons.lang.Validate.noNullElements(arrayArg, \"message\");",
                        "    org.apache.commons.lang.Validate.allElementsOfType("
                                + "collectionArg, Object.class, \"message\");",
                        "  }",
                        "}")
                .doTest();
    }

}
