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
import org.junit.Test;

public class ImmutablesBuilderMissingInitializationTest {

    @Test
    public void testPassesWithAllFieldsPopulated_usingBuilderMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").age(0).company(\"palantir\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithOptionalFieldOmitted_usingBuilderMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").age(10).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithFrom_usingBuilderMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person p1 = Person.builder().name(\"name\").age(20).build();",
                        "        Person.builder().from(p1).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenBuilderAssignedToVariable_usingBuilderMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.Builder builder = Person.builder();",
                        "        builder.build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithUninitializedFields_whenBuilderFromMethodThatSetsSomeFields() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        builder().build();",
                        "    }",
                        "    private static ImmutablePerson.Builder builder() {",
                        "        return new Person.Builder().name(\"name\").age(30);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithAllFieldsPopulated_usingExtendedConstructor() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new Person.Builder().name(\"name\").age(40).company(\"palantir\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithAllFieldsPopulated_usingImmutableConstructor() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new ImmutablePerson.Builder().name(\"name\").age(50).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWithOneMandatoryFieldOmitted() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder()",
                        "                .age(60)",
                        "                .company(\"palantir\")",
                        "                // BUG: Diagnostic contains: Some builder fields have not been initialized: "
                                + "name",
                        "                .build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWithAllFieldsOmitted() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name, age",
                        "        Person.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWithOneFieldOmitted_usingExtendedConstructor() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        new Person.Builder().age(70).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWithOneFieldOmitted_usingImmutableConstructor() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        new ImmutablePerson.Builder().age(80).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFailsWithOneFieldOmitted_fromLocalMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
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
    public void testSucceedsWithNoRequiredFields() {
        helper().addSourceLines(
                        "Bug.java",
                        "import java.util.Optional;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Bug {",
                        "    Optional<String> author();",
                        "}")
                .addSourceLines(
                        "ImmutableBug.java",
                        "import java.util.Objects;",
                        "import java.util.Optional;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Bug\", generator = \"Immutables\")",
                        "final class ImmutableBug implements Bug {",
                        "    private final @Nullable String author;",
                        "    private ImmutableBug(@Nullable String author) {",
                        "        this.author = author;",
                        "    }",
                        "    @Override",
                        "    public Optional<String> author() { return Optional.ofNullable(author); }",
                        "",
                        "    @Generated(from = \"Bug\", generator = \"Immutables\")",
                        "    public static class Builder {",
                        "        private @Nullable String author;",
                        "        public final Builder author(String author) {",
                        "            this.author = Objects.requireNonNull(author, \"author\");",
                        "            return this;",
                        "        }",
                        "        public Bug build() {",
                        "            return new ImmutableBug(author);",
                        "        }",
                        "    }",
                        "}")
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        new ImmutableBug.Builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testComputesMissingFieldNamesCorrectly() {
        helper().addSourceLines(
                        "Company.java",
                        "import java.util.Optional;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Company {",
                        "    int employeeCount();",
                        "}")
                .addSourceLines(
                        "ImmutableCompany.java",
                        "import java.util.Objects;",
                        "import java.util.Optional;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Company\", generator = \"Immutables\")",
                        "final class ImmutableCompany implements Company {",
                        "    private final int employeeCount;",
                        "    private ImmutableCompany(int employeeCount) {",
                        "        this.employeeCount = employeeCount;",
                        "    }",
                        "    @Override",
                        "    public int employeeCount() { return employeeCount; }",
                        "",
                        "    @Generated(from = \"Company\", generator = \"Immutables\")",
                        "    public static class Builder {",
                        "        private static final long INIT_BIT_EMPLOYEE_COUNT = 0x1L;",
                        "        private long initBits = 0x1L;",
                        "        private @Nullable Integer employeeCount;",
                        "        public final Builder employeeCount(int employeeCount) {",
                        "            this.employeeCount = employeeCount;",
                        "            initBits &= ~INIT_BIT_EMPLOYEE_COUNT;",
                        "            return this;",
                        "        }",
                        "        public Company build() {",
                        "            return new ImmutableCompany(employeeCount);",
                        "        }",
                        "    }",
                        "}")
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: "
                                + "employeeCount",
                        "        new ImmutableCompany.Builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesBuilderMissingInitialization.class, getClass());
    }

    private CompilationTestHelper helperWithImmutables() {
        return helper().addSourceLines(
                        "Person.java",
                        "import java.util.Optional;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Person {",
                        "    String name();",
                        "    int age();",
                        "    Optional<String> company();",
                        "    static Builder builder() {",
                        "        return new Builder();",
                        "    }",
                        "    class Builder extends ImmutablePerson.Builder {}",
                        "}")
                .addSourceLines(
                        "ImmutablePerson.java",
                        "import java.util.Objects;",
                        "import java.util.Optional;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Person\", generator = \"Immutables\")",
                        "final class ImmutablePerson implements Person {",
                        "    private final String name;",
                        "    private final int age;",
                        "    private final @Nullable String company;",
                        "    private ImmutablePerson(String name, int age, @Nullable String company) {",
                        "        this.name = name; this.age = age; this.company = company;",
                        "    }",
                        "    @Override",
                        "    public String name() { return name; }",
                        "    @Override",
                        "    public int age() { return age; }",
                        "    @Override",
                        "    public Optional<String> company() { return Optional.ofNullable(company); }",
                        "",
                        "    @Generated(from = \"Person\", generator = \"Immutables\")",
                        "    public static class Builder {",
                        "        private static final long INIT_BIT_NAME = 0x1L;",
                        "        private static final long INIT_BIT_AGE = 0x2L;",
                        "        private long initBits = 0x3L;",
                        "        private @Nullable String name;",
                        "        private @Nullable Integer age;",
                        "        private @Nullable String company;",
                        "        public final Builder name(String name) {",
                        "            this.name = Objects.requireNonNull(name, \"name\");",
                        "            initBits &= ~INIT_BIT_NAME;",
                        "            return this;",
                        "        }",
                        "        public final Builder age(int age) {",
                        "            this.age = age;",
                        "            initBits &= ~INIT_BIT_AGE;",
                        "            return this;",
                        "        }",
                        "        public final Builder company(String company) {",
                        "            this.company = Objects.requireNonNull(company, \"company\");",
                        "            return this;",
                        "        }",
                        "        public final Builder company(Optional<String> company) {",
                        "            this.company = company.orElse(null);",
                        "            return this;",
                        "        }",
                        "        public final Builder from(Person instance) {",
                        "            name(instance.name());",
                        "            age(instance.age());",
                        "            company(instance.company());",
                        "            return this;",
                        "        }",
                        "        public Person build() {",
                        "            if (initBits != 0) { throw new IllegalStateException(); }",
                        "            return new ImmutablePerson(name, age, company);",
                        "        }",
                        "    }",
                        "}");
    }
}
