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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public class ImmutablesStyleTest {

    @Test
    public void testInlineAnnotation() {
        helper().addSourceLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "// BUG: Diagnostic contains: ImmutablesStyle",
                        "public interface Person {}")
                .doTest();
    }

    @Test
    public void fixInlineAnnotation_topLevel() {
        fix().addInputLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public interface Person {}")
                .addOutputLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@SuppressWarnings(\"ImmutablesStyle\")",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public interface Person {}")
                .doTest();
    }

    @Test
    public void fixInlineAnnotation_enclosed() {
        fix().addInputLines(
                        "Enclosing.java",
                        "import org.immutables.value.Value;",
                        "public class Enclosing {",
                        "  @Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "  public interface Person {}",
                        "}")
                .addOutputLines(
                        "Enclosing.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "public class Enclosing {",
                        "  @Target(ElementType.TYPE)",
                        "  @Retention(RetentionPolicy.SOURCE)",
                        "  @Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "  @interface PersonStyle {}",
                        "  @PersonStyle",
                        "  public interface Person {}",
                        "}")
                .doTest();
    }

    @Test
    public void testMetaAnnotation_defaultRetention() {
        helper().addSourceLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "// BUG: Diagnostic contains: ImmutablesStyle",
                        "public @interface MyMetaAnnotation {}")
                .addSourceLines("Person.java", "@MyMetaAnnotation", "public interface Person {}")
                .doTest();
    }

    @Test
    public void fixMetaAnnotation_defaultRetention() {
        fix().addInputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "public @interface MyMetaAnnotation {}")
                .addOutputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Retention(RetentionPolicy.SOURCE)",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "public @interface MyMetaAnnotation {}")
                .doTest();
    }

    @Test
    public void testMetaAnnotation_classRetention() {
        helper().addSourceLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Retention(RetentionPolicy.CLASS)\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "// BUG: Diagnostic contains: ImmutablesStyle",
                        "public @interface MyMetaAnnotation {}")
                .addSourceLines("Person.java", "@MyMetaAnnotation", "public interface Person {}")
                .doTest();
    }

    @Test
    public void fixMetaAnnotation_classRetention() {
        fix().addInputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(RetentionPolicy.CLASS)",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public @interface MyMetaAnnotation {}")
                .addOutputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(RetentionPolicy.SOURCE)",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public @interface MyMetaAnnotation {}")
                .doTest();
    }

    @Test
    public void testMetaAnnotation_runtimeRetention() {
        helper().addSourceLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Retention(RetentionPolicy.RUNTIME)\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "// BUG: Diagnostic contains: ImmutablesStyle",
                        "public @interface MyMetaAnnotation {}")
                .addSourceLines("Person.java", "@MyMetaAnnotation", "public interface Person {}")
                .doTest();
    }

    @Test
    public void fixMetaAnnotation_runtimeRetention() {
        fix().addInputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(value = RetentionPolicy.RUNTIME)",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public @interface MyMetaAnnotation {}")
                .addOutputLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})",
                        "@Retention(value = RetentionPolicy.SOURCE)",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)",
                        "public @interface MyMetaAnnotation {}")
                .doTest();
    }

    @Test
    public void testMetaAnnotation_sourceRetention() {
        helper().addSourceLines(
                        "MyMetaAnnotation.java",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Retention(RetentionPolicy.SOURCE)\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "public @interface MyMetaAnnotation {}")
                .addSourceLines("Person.java", "@MyMetaAnnotation", "public interface Person {}")
                .doTest();
    }

    @Test
    public void testOtherAnnotation() {
        helper().addSourceLines("MyOtherAnnotation.java", "public @interface MyOtherAnnotation {}")
                .addSourceLines("Person.java", "@MyOtherAnnotation", "public interface Person {}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesStyle.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(ImmutablesStyle.class, getClass());
    }
}
