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
    public void testMustNotUseClassVariants() throws Exception {
        positive("JsonTypeInfo.Id.CLASS");
        positive("JsonTypeInfo.Id.MINIMAL_CLASS");
        positive("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS");
        positive("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS");
    }

    @Test
    public void testMayUseNoneNameCustomVariants() throws Exception {
        negative("JsonTypeInfo.Id.NONE");
        negative("JsonTypeInfo.Id.NAME");
        negative("JsonTypeInfo.Id.CUSTOM");
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NONE");
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME");
        negative("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CUSTOM");
    }

    private void positive(String variant) throws Exception {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                "@JsonTypeInfo(use = " + variant + ")",
                "class Bean {}"
        ).doTest();
    }

    private void negative(String variant) throws Exception {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                "@JsonTypeInfo(use = " + variant + ")",
                "class Bean {}"
        ).doTest();
    }
}
