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
import org.junit.jupiter.api.Test;

public final class DangerousJsonTypeInfoUsageTests {

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
    public void testClass_IdQualified() {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;",
                        "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                        "@JsonTypeInfo(use = Id.CLASS)",
                        "class Bean {}")
                .doTest();
    }

    @Test
    public void testMinimalClass_IdQualified() {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;",
                        "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                        "@JsonTypeInfo(use = Id.MINIMAL_CLASS)",
                        "class Bean {}")
                .doTest();
    }

    @Test
    public void testClass_ClassQualified() {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS;",
                        "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                        "@JsonTypeInfo(use = CLASS)",
                        "class Bean {}")
                .doTest();
    }

    @Test
    public void testMinimalClass_MinimalClassQualified() {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS;",
                        "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                        "@JsonTypeInfo(use = MINIMAL_CLASS)",
                        "class Bean {}")
                .doTest();
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

    @Test
    public void testObjectMapper_enableDefaultTyping() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.fasterxml.jackson.databind.ObjectMapper;",
                        "class Test {",
                        "// BUG: Diagnostic contains: Must not use a Jackson ObjectMapper with default typings",
                        "  Object om = new ObjectMapper().enableDefaultTyping();",
                        "}")
                .doTest();
    }

    @Test
    public void testObjectMapper_enableDefaultTypingAsProperty() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.fasterxml.jackson.databind.ObjectMapper;",
                        "class Test {",
                        "  Object om = new ObjectMapper()",
                        "// BUG: Diagnostic contains: Must not use a Jackson ObjectMapper with default typings",
                        "    .enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, \"prop\");",
                        "}")
                .doTest();
    }

    @Test
    public void testObjectMapper_activateDefaultTyping() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.fasterxml.jackson.databind.ObjectMapper;",
                        "import com.fasterxml.jackson.databind.jsontype.DefaultBaseTypeLimitingValidator;",
                        "class Test {",
                        "// BUG: Diagnostic contains: Must not use a Jackson ObjectMapper with default typings",
                        "  Object om = new ObjectMapper().activateDefaultTyping(new"
                                + " DefaultBaseTypeLimitingValidator());",
                        "}")
                .doTest();
    }

    @Test
    public void testObjectMapper_activateDefaultTypingAsProperty() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.fasterxml.jackson.databind.ObjectMapper;",
                        "import com.fasterxml.jackson.databind.jsontype.DefaultBaseTypeLimitingValidator;",
                        "class Test {",
                        "  Object om = new ObjectMapper()",
                        "// BUG: Diagnostic contains: Must not use a Jackson ObjectMapper with default typings",
                        "    .activateDefaultTypingAsProperty(",
                        "      new DefaultBaseTypeLimitingValidator(),",
                        "      ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,",
                        "      \"prop\");",
                        "}")
                .doTest();
    }

    @Test
    public void testObjectMapper_setDefaultTyping() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.fasterxml.jackson.databind.ObjectMapper;",
                        "import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;",
                        "class Test {",
                        "  void f(TypeResolverBuilder value) {",
                        "    new ObjectMapper()",
                        "// BUG: Diagnostic contains: Must not use a Jackson ObjectMapper with default typings",
                        "      .setDefaultTyping(value);",
                        "  }",
                        "}")
                .doTest();
    }

    private void positive(String variant) {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "// BUG: Diagnostic contains: Must not use Jackson @JsonTypeInfo annotation",
                        "@JsonTypeInfo(use = " + variant + ")",
                        "class Bean {}")
                .doTest();
    }

    private void negative(String variant) {
        helper().addSourceLines(
                        "Bean.java",
                        "import com.fasterxml.jackson.annotation.JsonTypeInfo;",
                        "@JsonTypeInfo(use = " + variant + ")",
                        "class Bean {}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DangerousJsonTypeInfoUsage.class, getClass());
    }
}
