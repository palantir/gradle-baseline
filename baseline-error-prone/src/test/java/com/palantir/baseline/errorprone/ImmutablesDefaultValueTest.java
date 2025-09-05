/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

public class ImmutablesDefaultValueTest {

    @Test
    public void testDefaultMethodWithoutAnnotation_immutableAnnotation() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testDefaultMethodWithoutAnnotation_jsonDeserializeAnnotation() {
        helper().addSourceLines(
                        "Config.java",
                        "import com.fasterxml.jackson.databind.annotation.JsonDeserialize;",
                        "@JsonDeserialize",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testDefaultMethodWithAnnotation() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    @Value.Default",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testAbstractMethod() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    int timeout();",
                        "}")
                .doTest();
    }

    @Test
    public void testNonImmutableInterface() {
        helper().addSourceLines(
                        "Config.java",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testSuppressedOnMethod() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    @SuppressWarnings(\"ImmutablesDefaultValue\")",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testSuppressedOnInterface() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@SuppressWarnings(\"ImmutablesDefaultValue\")",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testMultipleDefaultMethods() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "    @Value.Default",
                        "    default boolean enabled() { return true; }",
                        "    // BUG: Diagnostic contains: `default` method 'retries' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int retries() { return 3; }",
                        "}")
                .doTest();
    }

    @Test
    public void testDefaultAsDefaultStyleTrue() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(defaultAsDefault = true)",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testDefaultAsDefaultStyleFalse() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(defaultAsDefault = false)",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testDefaultAsDefaultStyleTrueWithOtherAttributes() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(defaultAsDefault = true, visibility ="
                                + " Value.Style.ImplementationVisibility.PUBLIC)",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testStyleWithoutDefaultAsDefault() {
        helper().addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void fixDefaultMethodWithoutAnnotation() {
        fix().addInputLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .addOutputLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    @Value.Default",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testMetaAnnotationWithDefaultAsDefaultTrue() {
        helper().addSourceLines(
                        "ImmutablesStyle.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(RetentionPolicy.SOURCE)",
                        "@Value.Style(defaultAsDefault = true, visibility ="
                                + " Value.Style.ImplementationVisibility.PACKAGE)",
                        "public @interface ImmutablesStyle {}")
                .addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@ImmutablesStyle",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testMetaAnnotationWithDefaultAsDefaultFalse() {
        helper().addSourceLines(
                        "ImmutablesStyle.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(RetentionPolicy.SOURCE)",
                        "@Value.Style(defaultAsDefault = false, visibility ="
                                + " Value.Style.ImplementationVisibility.PACKAGE)",
                        "public @interface ImmutablesStyle {}")
                .addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@ImmutablesStyle",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    @Test
    public void testMetaAnnotationWithoutDefaultAsDefault() {
        helper().addSourceLines(
                        "ImmutablesStyle.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(RetentionPolicy.SOURCE)",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)",
                        "public @interface ImmutablesStyle {}")
                .addSourceLines(
                        "Config.java",
                        "import org.immutables.value.Value;",
                        "@ImmutablesStyle",
                        "@Value.Immutable",
                        "public interface Config {",
                        "    String name();",
                        "    // BUG: Diagnostic contains: `default` method 'timeout' in immutable interface 'Config'"
                                + " needs @Value.Default to be included as a field in the generated class",
                        "    default int timeout() { return 30; }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesDefaultValue.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(ImmutablesDefaultValue.class, getClass());
    }
}
