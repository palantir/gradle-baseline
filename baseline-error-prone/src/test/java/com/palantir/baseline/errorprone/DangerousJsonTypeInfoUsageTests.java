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
import org.junit.Before;
import org.junit.Test;

public final class DangerousJsonTypeInfoUsageTests {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousJsonTypeInfoUsage.class, getClass());
    }

    @Test
    public void testClass() {
        positive("JsonTypeInfo.Id.CLASS");
    }

    @Test
    public void testClass_fullyQualified() {
        positive("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS");
    }

    @Test
    public void testMinimalClass() {
        positive("JsonTypeInfo.Id.MINIMAL_CLASS");
    }

    @Test
    public void testMinimalClass_fullyQualified() {
        positive("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS");
    }

    @Test
    public void testNone() {
        negative("JsonTypeInfo.Id.NONE");
    }

    @Test
    public void testNone_fullyQualified() {
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NONE");
    }

    @Test
    public void testName() {
        negative("JsonTypeInfo.Id.NAME");
    }

    @Test
    public void testName_fullyQualified() {
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME");
    }

    @Test
    public void testCustom() {
        negative("JsonTypeInfo.Id.CUSTOM");
    }

    @Test
    public void testCustom_fullyQualified() {
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CUSTOM");
    }

    private void positive(String variant) {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                "@JsonTypeInfo(use = " + variant + ")",
                "class Bean {}").doTest();
    }

    private void negative(String variant) {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                "@JsonTypeInfo(use = " + variant + ")",
                "class Bean {}").doTest();
    }
}
