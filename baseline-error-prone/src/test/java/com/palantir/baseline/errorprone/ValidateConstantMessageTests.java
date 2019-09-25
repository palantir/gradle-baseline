/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
public final class ValidateConstantMessageTests {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(ValidateConstantMessage.class, getClass());
    }

    @Test
    public void testValidateIsTrueNoMessage() {
        testPassBoth("Validate.isTrue(param != \"string\");");
    }

    @Test
    public void testValidateIsTrueConstantMessageNoArgs() {
        testPassBoth("Validate.isTrue(param != \"string\", \"constant\");");
    }

    @Test
    public void testValidateIsTrueConstantMessageArgs() {
        testPassLang3Only("Validate.isTrue(param != \"string\", \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateIsTrueNonConstantMessageNoArgs() {
        testFailBoth("Validate.isTrue(param != \"string\", \"constant\" + param);");
    }

    @Test
    public void testValidateIsTrueNonConstantMessageArgs() {
        testFailLang3Only("Validate.isTrue(param != \"string\", \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateIsTrueNonConstantMessageDouble() {
        testFailBoth("Validate.isTrue(param != \"string\", \"constant\" + param, 0.0);");
    }

    @Test
    public void testValidateIsTrueNonConstantMessageLong() {
        testFailBoth("Validate.isTrue(param != \"string\", \"constant\" + param, 123L);");
    }

    @Test
    public void testValidateNotNullNoMessage() {
        // CHECKSTYLE:OFF
        testPassBoth("Validate.notNull(param);");
        // CHECKSTYLE:ON
    }

    @Test
    public void testValidateNotNullConstantMessageNoArgs() {
        testPassBoth("Validate.notNull(param, \"constant\");");
    }

    @Test
    public void testValidateNotNullConstantMessageArgs() {
        testPassLang3Only("Validate.notNull(param, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotNullNonConstantMessageNoArgs() {
        testFailBoth("Validate.notNull(param, \"constant\" + param);");
    }

    @Test
    public void testValidateNotNullNonConstantMessageArgs() {
        testFailLang3Only("Validate.notNull(param, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNoMessageArray() {
        testPassBoth("Validate.notEmpty(arrayArg);");
    }

    @Test
    public void testValidateNotEmptyConstantMessageArrayNoArgs() {
        testPassBoth("Validate.notEmpty(arrayArg, \"constant\");");
    }

    @Test
    public void testValidateNotEmptyConstantMessageArrayArgs() {
        testPassLang3Only("Validate.notEmpty(arrayArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageArrayNoArgs() {
        testFailBoth("Validate.notEmpty(arrayArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageArrayArgs() {
        testFailLang3Only("Validate.notEmpty(arrayArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNoMessageCollection() {
        testPassBoth("Validate.notEmpty(collectionArg);");
    }

    @Test
    public void testValidateNotEmptyConstantMessageCollectionNoArgs() {
        testPassBoth("Validate.notEmpty(collectionArg, \"constant\");");
    }

    @Test
    public void testValidateNotEmptyConstantMessageCollectionArgs() {
        testPassLang3Only("Validate.notEmpty(collectionArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageCollectionNoArgs() {
        testFailBoth("Validate.notEmpty(collectionArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageCollectionArgs() {
        testFailLang3Only("Validate.notEmpty(collectionArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNoMessageMap() {
        testPassBoth("Validate.notEmpty(mapArg);");
    }

    @Test
    public void testValidateNotEmptyConstantMessageMapNoArgs() {
        testPassBoth("Validate.notEmpty(mapArg, \"constant\");");
    }

    @Test
    public void testValidateNotEmptyConstantMessageMapArgs() {
        testPassLang3Only("Validate.notEmpty(mapArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageMapNoArgs() {
        testFailBoth("Validate.notEmpty(mapArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageMapArgs() {
        testFailLang3Only("Validate.notEmpty(mapArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNoMessageChars() {
        testPassBoth("Validate.notEmpty(stringArg);");
    }

    @Test
    public void testValidateNotEmptyConstantMessageCharsNoArgs() {
        testPassBoth("Validate.notEmpty(stringArg, \"constant\");");
    }

    @Test
    public void testValidateNotEmptyConstantMessageCharsArgs() {
        testPassLang3Only("Validate.notEmpty(stringArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageCharsNoArgs() {
        testFailBoth("Validate.notEmpty(stringArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotEmptyNonConstantMessageCharsArgs() {
        testFailLang3Only("Validate.notEmpty(stringArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotBlankNoMessage() {
        testPassLang3Only("Validate.notBlank(stringArg);");
    }

    @Test
    public void testValidateNotBlankConstantMessageNoArgs() {
        testPassLang3Only("Validate.notBlank(stringArg, \"constant\");");
    }

    @Test
    public void testValidateNotBlankConstantMessageArgs() {
        testPassLang3Only("Validate.notBlank(stringArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotBlankNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.notBlank(stringArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotBlankNonConstantMessageArgs() {
        testFailLang3Only("Validate.notBlank(stringArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNoNullElementsNoMessageIterable() {
        testPassLang3Only("Validate.noNullElements(iterableArg);");
    }

    @Test
    public void testValidateNoNullElementsConstantMessageIterableNoArgs() {
        testPassLang3Only("Validate.noNullElements(iterableArg, \"constant\");");
    }

    @Test
    public void testValidateNoNullElementsConstantMessageIterableArgs() {
        testPassLang3Only("Validate.noNullElements(iterableArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNoNullElementsNonConstantMessageIterableNoArgs() {
        testFailLang3Only("Validate.noNullElements(iterableArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNoNullElementsNonConstantMessageIterableArgs() {
        testFailLang3Only("Validate.noNullElements(iterableArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNoNullElementsNoMessageArray() {
        testPassBoth("Validate.noNullElements(arrayArg);");
    }

    @Test
    public void testValidateNoNullElementsConstantMessageArrayNoArgs() {
        testPassBoth("Validate.noNullElements(arrayArg, \"constant\");");
    }

    @Test
    public void testValidateNoNullElementsConstantMessageArrayArgs() {
        testPassLang3Only("Validate.noNullElements(arrayArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNoNullElementsNonConstantMessageArrayNoArgs() {
        testFailBoth("Validate.noNullElements(arrayArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNoNullElementsNonConstantMessageArrayArgs() {
        testFailLang3Only("Validate.noNullElements(arrayArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateValidIndexNoMessageArray() {
        testPassLang3Only("Validate.validIndex(arrayArg, 1);");
    }

    @Test
    public void testValidateValidIndexConstantMessageArrayNoArgs() {
        testPassLang3Only("Validate.validIndex(arrayArg, 1, \"constant\");");
    }

    @Test
    public void testValidateValidIndexConstantMessageArrayArgs() {
        testPassLang3Only("Validate.validIndex(arrayArg, 1, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageArrayNoArgs() {
        testFailLang3Only("Validate.validIndex(arrayArg, 1, \"constant\" + param);");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageArrayArgs() {
        testFailLang3Only("Validate.validIndex(arrayArg, 1, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateValidIndexNoMessageCollection() {
        testPassLang3Only("Validate.validIndex(collectionArg, 1);");
    }

    @Test
    public void testValidateValidIndexConstantMessageCollectionNoArgs() {
        testPassLang3Only("Validate.validIndex(collectionArg, 1, \"constant\");");
    }

    @Test
    public void testValidateValidIndexConstantMessageCollectionArgs() {
        testPassLang3Only("Validate.validIndex(collectionArg, 1, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageCollectionNoArgs() {
        testFailLang3Only("Validate.validIndex(collectionArg, 1, \"constant\" + param);");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageCollectionArgs() {
        testFailLang3Only("Validate.validIndex(collectionArg, 1, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateValidIndexNoMessageChars() {
        testPassLang3Only("Validate.validIndex(stringArg, 1);");
    }

    @Test
    public void testValidateValidIndexConstantMessageCharsNoArgs() {
        testPassLang3Only("Validate.validIndex(stringArg, 1, \"constant\");");
    }

    @Test
    public void testValidateValidIndexConstantMessageCharsArgs() {
        testPassLang3Only("Validate.validIndex(stringArg, 1, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageCharsNoArgs() {
        testFailLang3Only("Validate.validIndex(stringArg, 1, \"constant\" + param);");
    }

    @Test
    public void testValidateValidIndexNonConstantMessageCharsArgs() {
        testFailLang3Only("Validate.validIndex(stringArg, 1, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateValidStateNoMessage() {
        testPassLang3Only("Validate.validState(bArg);");
    }

    @Test
    public void testValidateValidStateConstantMessageNoArgs() {
        testPassLang3Only("Validate.validState(bArg, \"constant\");");
    }

    @Test
    public void testValidateValidStateConstantMessageArgs() {
        testPassLang3Only("Validate.validState(bArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateValidStateNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.validState(bArg, \"constant\" + param);");
    }

    @Test
    public void testValidateValidStateNonConstantMessageArgs() {
        testFailLang3Only("Validate.validState(bArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateMatchesPatternNoMessage() {
        testPassLang3Only("Validate.matchesPattern(stringArg, \"[A-Z]+\");");
    }

    @Test
    public void testValidateMatchesPatternConstantMessageNoArgs() {
        testPassLang3Only("Validate.matchesPattern(stringArg, \"[A-Z]+\", \"constant\");");
    }

    @Test
    public void testValidateMatchesPatternConstantMessageArgs() {
        testPassLang3Only("Validate.matchesPattern(stringArg, \"[A-Z]+\", \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateMatchesPatternNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.matchesPattern(stringArg, \"[A-Z]+\", \"constant\" + param);");
    }

    @Test
    public void testValidateMatchesPatternNonConstantMessageArgs() {
        testFailLang3Only("Validate.matchesPattern(stringArg, \"[A-Z]+\", \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateNotNanNoMessage() {
        testPassLang3Only("Validate.notNaN(dArg);");
    }

    @Test
    public void testValidateNotNanConstantMessageNoArgs() {
        testPassLang3Only("Validate.notNaN(dArg, \"constant\");");
    }

    @Test
    public void testValidateNotNanConstantMessageArgs() {
        testPassLang3Only("Validate.notNaN(dArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateNotNanNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.notNaN(dArg, \"constant\" + param);");
    }

    @Test
    public void testValidateNotNanNonConstantMessageArgs() {
        testFailLang3Only("Validate.notNaN(dArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateFiniteNoMessage() {
        testPassLang3Only("Validate.finite(dArg);");
    }

    @Test
    public void testValidateFiniteConstantMessageNoArgs() {
        testPassLang3Only("Validate.finite(dArg, \"constant\");");
    }

    @Test
    public void testValidateFiniteConstantMessageArgs() {
        testPassLang3Only("Validate.finite(dArg, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateFiniteNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.finite(dArg, \"constant\" + param);");
    }

    @Test
    public void testValidateFiniteNonConstantMessageArgs() {
        testFailLang3Only("Validate.finite(dArg, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNoMessageLong() {
        testPassLang3Only("Validate.inclusiveBetween(0L, 100L, 50L);");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageLongNoArgs() {
        testPassLang3Only("Validate.inclusiveBetween(0L, 100L, 50L, \"constant\");");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageLongArgs() {
        testPassLang3Only("Validate.inclusiveBetween(0L, 100L, 50L, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageLongNoArgs() {
        testFailLang3Only("Validate.inclusiveBetween(0L, 100L, 50L, \"constant\" + param);");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageLongArgs() {
        testFailLang3Only("Validate.inclusiveBetween(0L, 100L, 50L, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNoMessageDouble() {
        testPassLang3Only("Validate.inclusiveBetween(0.0, 1.0, 0.5);");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageDoubleNoArgs() {
        testPassLang3Only("Validate.inclusiveBetween(0.0, 1.0, 0.5, \"constant\");");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageDoubleArgs() {
        testPassLang3Only("Validate.inclusiveBetween(0.0, 1.0, 0.5, \"constant\", \"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageDoubleNoArgs() {
        testFailLang3Only("Validate.inclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param);");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageDoubleArgs() {
        testFailLang3Only("Validate.inclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNoMessageComparable() {
        testPassLang3Only("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE);");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageComparableNoArgs() {
        testPassLang3Only("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, \"constant\");");
    }

    @Test
    public void testValidateInclusiveBetweenConstantMessageComparableArgs() {
        testPassLang3Only("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, \"constant %s\", "
                + "\"arg\");");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageComparableNoArgs() {
        testFailLang3Only("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param);");
    }

    @Test
    public void testValidateInclusiveBetweenNonConstantMessageComparableArgs() {
        testFailLang3Only("Validate.inclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNoMessageLong() {
        testPassLang3Only("Validate.exclusiveBetween(0L, 100L, 50L);");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageLongNoArgs() {
        testPassLang3Only("Validate.exclusiveBetween(0L, 100L, 50L, \"constant\");");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageLongArgs() {
        testPassLang3Only("Validate.exclusiveBetween(0L, 100L, 50L, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageLongNoArgs() {
        testFailLang3Only("Validate.exclusiveBetween(0L, 100L, 50L, \"constant\" + param);");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageLongArgs() {
        testFailLang3Only("Validate.exclusiveBetween(0L, 100L, 50L, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNoMessageDouble() {
        testPassLang3Only("Validate.exclusiveBetween(0.0, 1.0, 0.5);");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageDoubleNoArgs() {
        testPassLang3Only("Validate.exclusiveBetween(0.0, 1.0, 0.5, \"constant\");");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageDoubleArgs() {
        testPassLang3Only("Validate.exclusiveBetween(0.0, 1.0, 0.5, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageDoubleNoArgs() {
        testFailLang3Only("Validate.exclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param);");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageDoubleArgs() {
        testFailLang3Only("Validate.exclusiveBetween(0.0, 1.0, 0.5, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNoMessageComparable() {
        testPassLang3Only("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE);");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageComparableNoArgs() {
        testPassLang3Only("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, \"constant\");");
    }

    @Test
    public void testValidateExclusiveBetweenConstantMessageComparableArgs() {
        testPassLang3Only("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, \"constant %s\", "
                + "\"arg\");");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageComparableNoArgs() {
        testFailLang3Only("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param);");
    }

    @Test
    public void testValidateExclusiveBetweenNonConstantMessageComparableArgs() {
        testFailLang3Only("Validate.exclusiveBetween(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, "
                + "\"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateIsInstanceOfNoMessage() {
        testPassLang3Only("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE);");
    }

    @Test
    public void testValidateIsInstanceOfConstantMessageNoArgs() {
        testPassLang3Only("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE, \"constant\");");
    }

    @Test
    public void testValidateIsInstanceOfConstantMessageArgs() {
        testPassLang3Only("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateIsInstanceOfNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE, \"constant\" + param);");
    }

    @Test
    public void testValidateIsInstanceOfNonConstantMessageArgs() {
        testFailLang3Only("Validate.isInstanceOf(BigDecimal.class, BigDecimal.ONE, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateIsAssignableFromNoMessage() {
        testPassLang3Only("Validate.isAssignableFrom(Object.class, BigDecimal.class);");
    }

    @Test
    public void testValidateIsAssignableFromConstantMessageNoArgs() {
        testPassLang3Only("Validate.isAssignableFrom(Object.class, BigDecimal.class, \"constant\");");
    }

    @Test
    public void testValidateIsAssignableFromConstantMessageArgs() {
        testPassLang3Only("Validate.isAssignableFrom(Object.class, BigDecimal.class, \"constant %s\", \"arg\");");
    }

    @Test
    public void testValidateIsAssignableFromNonConstantMessageNoArgs() {
        testFailLang3Only("Validate.isAssignableFrom(Object.class, BigDecimal.class, \"constant\" + param);");
    }

    @Test
    public void testValidateIsAssignableFromNonConstantMessageArgs() {
        testFailLang3Only("Validate.isAssignableFrom(Object.class, BigDecimal.class, \"constant\" + param, \"arg\");");
    }

    @Test
    public void testValidateAllElementsOfTypeNoMessage() {
        testPassLang2Only("Validate.allElementsOfType(collectionArg, BigDecimal.class);");
    }

    @Test
    public void testValidateAllElementsOfTypeConstantMessageNoArgs() {
        testPassLang2Only("Validate.allElementsOfType(collectionArg, BigDecimal.class, \"constant\");");
    }

    @Test
    public void testValidateAllElementsOfTypeNonConstantMessageNoArgs() {
        testFailLang2Only("Validate.allElementsOfType(collectionArg, BigDecimal.class, \"constant\" + param);");
    }

    private void testFailLang3Only(String call) {
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

    private void testPassLang3Only(String call) {
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
                        "    " + call,
                        "  }",
                        "}")
                .doTest();
    }

    private void testFailLang2Only(String call) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.apache.commons.lang.Validate;",
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

    private void testPassLang2Only(String call) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.apache.commons.lang.Validate;",
                        "import java.math.BigDecimal;",
                        "import java.util.Collection;",
                        "import java.util.Map;",
                        "class Test {",
                        "  void f(String param, boolean bArg, int iArg, Object oArg, Integer[] arrayArg, "
                                + "Collection<String> collectionArg, Map<String, String> mapArg, String stringArg, "
                                + "Iterable<String> iterableArg, double dArg) {",
                        "    " + call,
                        "  }",
                        "}")
                .doTest();
    }

    private void testFailBoth(String call) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.math.BigDecimal;",
                        "import java.util.Collection;",
                        "import java.util.Map;",
                        "class Test {",
                        "  void f(String param, boolean bArg, int iArg, Object oArg, Integer[] arrayArg, "
                                + "Collection<String> collectionArg, Map<String, String> mapArg, String stringArg, "
                                + "Iterable<String> iterableArg, double dArg) {",
                        "    // BUG: Diagnostic contains: non-constant message",
                        "    org.apache.commons.lang." + call,
                        "    // BUG: Diagnostic contains: non-constant message",
                        "    org.apache.commons.lang3." + call,
                        "  }",
                        "}")
                .doTest();
    }

    private void testPassBoth(String call) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.math.BigDecimal;",
                        "import java.util.Collection;",
                        "import java.util.Map;",
                        "class Test {",
                        "  void f(String param, boolean bArg, int iArg, Object oArg, Integer[] arrayArg, "
                                + "Collection<String> collectionArg, Map<String, String> mapArg, String stringArg, "
                                + "Iterable<String> iterableArg, double dArg) {",
                        "    org.apache.commons.lang." + call,
                        "    org.apache.commons.lang3." + call,
                        "  }",
                        "}")
                .doTest();
    }
}
