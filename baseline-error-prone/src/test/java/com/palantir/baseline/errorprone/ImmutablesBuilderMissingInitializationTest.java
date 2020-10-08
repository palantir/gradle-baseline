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
import com.palantir.baseline.errorprone.immutablebuildersmissinginitialization.ImmutablePerson;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;
import org.junit.Test;

public class ImmutablesBuilderMissingInitializationTest {
    @Test
    public void testPassesWhenAllFieldsPopulated() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new Person.Builder().name(\"name\").age(100).partner(\"partner\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenOptionalFieldOmitted() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new Person.Builder().name(\"name\").age(100).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithFrom() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person p1 = new Person.Builder().name(\"name\").age(100).build();",
                        "        new Person.Builder().from(p1).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenBuilderAssignedToVariable() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.Builder builder = new Person.Builder();",
                        "        builder.build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithUninitializedFields_whenBuilderFromMethodThatSetsSomeFields() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        builder().build();",
                        "    }",
                        "    private static ImmutablePerson.Builder builder() {",
                        "        return new ImmutablePerson.Builder().name(\"name\").age(100);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenAllFieldsPopulated_usingInterfaceBuilderMethod() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").age(100).partner(\"partner\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenAllFieldsPopulated_usingImmutableBuilderMethod() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("NotOvershadowedType"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        ImmutableNotOvershadowedType.builder().name(\"name\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenAllFieldsPopulated_usingImmutableConstructor() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new ImmutablePerson.Builder().name(\"name\").age(100).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenNoRequiredFields() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("TypeWithNoMandatoryFields"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        ImmutableTypeWithNoMandatoryFields.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenAllFieldsPopulated_withCustomMethodStyle() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("TypeWithCustomMethodStyle"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        ImmutableTypeWithCustomMethodStyle.builder().setValue(\"value\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenUnsupportedGetterFormat_withCustomStyleOnPackage() {
        helper().addSourceLines(
                        "package-info.java",
                        "@org.immutables.value.Value.Style(get = \"*unsupportedGetFormat\")",
                        "package " + getClass().getPackage().getName() + ";")
                .addSourceLines(
                        "MyTest.java",
                        "import com.palantir.baseline.errorprone.ImmutableTypeWithNoStyleAnnotations;",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        ImmutableTypeWithNoStyleAnnotations.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenAllFieldsPopulated_withImmutableOnAbstractClass() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("ClassType"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        ImmutableClassType.builder().value(\"value\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneMandatoryFieldOmitted() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder()",
                        "                .age(100)",
                        "                .partner(\"partner\")",
                        "                // BUG: Diagnostic contains: Some builder fields have not been initialized: "
                                + "name",
                        "                .build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenAllFieldsOmitted() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name, age",
                        "        new Person.Builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneFieldOmitted_usingImmutableConstructor() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        new ImmutablePerson.Builder().age(100).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneFieldOmitted_usingInterfaceBuilderMethod() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        Person.builder().age(100).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneFieldOmitted_usingSimulatedBuilderMethod() {
        // This simulates using Person.builder() which delegates to ImmutablePerson.builder(), when Person is in the
        // same compilation unit
        helper().addSourceLines(
                        "Helper.java",
                        importImmutable("NotOvershadowedType"),
                        "public class Helper {",
                        "    public static ImmutableNotOvershadowedType.Builder builder() {",
                        "        return ImmutableNotOvershadowedType.builder();",
                        "    }",
                        "}")
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        Helper.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneFieldOmitted_fromLocalMethod() {
        helper().addSourceLines(
                        "MyTest.java",
                        importInterface("Person"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name, age",
                        "        getBuilder().build();",
                        "    }",
                        "    private static Person.Builder getBuilder() {",
                        "        return new Person.Builder();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWhenOneFieldOmitted_withCustomMethodStyle() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("TypeWithCustomMethodStyle"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: value",
                        "        ImmutableTypeWithCustomMethodStyle.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenOneFieldOmitted_withImmutableOnAbstractClass() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("ClassType"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: value",
                        "        ImmutableClassType.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testComputesMissingFieldNamesCorrectly() {
        helper().addSourceLines(
                        "MyTest.java",
                        importImmutable("TypeWithLongNames;"),
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: "
                                + "mandatoryFieldWithLongName",
                        "        ImmutableTypeWithLongNames.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesBuilderMissingInitialization.class, getClass());
    }

    private String importInterface(String interfaceName) {
        return String.format("import %s.%s;", getClass().getCanonicalName(), interfaceName);
    }

    private String importImmutable(String interfaceName) {
        return String.format(
                "import %s.immutablebuildersmissinginitialization.Immutable%s;",
                getClass().getPackage().getName(), interfaceName);
    }

    @Value.Immutable
    @Value.Style(
            visibility = ImplementationVisibility.PUBLIC,
            overshadowImplementation = true,
            packageGenerated = "*.immutablebuildersmissinginitialization")
    public interface Person {
        String name();

        int age();

        Optional<String> partner();

        class Builder extends ImmutablePerson.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }

    @Value.Immutable
    @DefaultImmutableStyle
    public interface NotOvershadowedType {
        String name();
    }

    @Value.Immutable
    @DefaultImmutableStyle
    public interface TypeWithNoMandatoryFields {
        Optional<String> notRequired();

        List<String> list();

        @Nullable
        String maybeString();
    }

    @Value.Immutable
    @DefaultImmutableStyle
    public interface TypeWithLongNames {
        String mandatoryFieldWithLongName();
    }

    @Value.Immutable
    @DefaultImmutableStyle
    public abstract static class ClassType {
        public abstract String value();
    }

    @Value.Immutable
    public interface TypeWithNoStyleAnnotations {
        String value();
    }

    @Value.Immutable
    @CustomStyleAnnotation
    public interface TypeWithCustomMethodStyle {
        String useValue();
    }

    @Value.Style(
            visibility = ImplementationVisibility.PUBLIC,
            packageGenerated = "*.immutablebuildersmissinginitialization")
    private @interface DefaultImmutableStyle {}

    @Value.Style(
            visibility = ImplementationVisibility.PUBLIC,
            init = "set*",
            get = "use*",
            packageGenerated = "*.immutablebuildersmissinginitialization")
    private @interface CustomStyleAnnotation {}
}
