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
    public void testPassesWithAllFieldsPopulated() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").currentAge(70).company(\"palantir\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithOptionalFieldOmitted() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").currentAge(60).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWithFrom() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person p1 = Person.builder().name(\"name\").currentAge(50).build();",
                        "        Person.builder().from(p1).build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenBuilderAssignedToVariable() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.Builder Builder = Person.builder();",
                        "        builder.build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenBuilderFromLocalMethod() {
        helperWithImmutables()
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        getBuilder().build();",
                        "    }",
                        "    private static Person.Builder getBuilder() {",
                        "        return new Person.Builder();",
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
                        "        new Person.Builder().name(\"name\").currentAge(40).company(\"palantir\").build();",
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
                        "        new ImmutablePerson.Builder().name(\"name\").currentAge(30).build();",
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
                        "                .currentAge(20)",
                        "                .company(\"palantir\")",
                        "                // BUG: Diagnostic contains: Some builder fields have not been initialized: ",
                        "                // name",
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
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name, ",
                        "        // currentAge",
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
                        "        new Person.Builder().currentAge(10).build();",
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
                        "        new ImmutablePerson.Builder().currentAge(0).build();",
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
                        "    static Builder builder() {",
                        "        return new Builder();",
                        "    }",
                        "}")
                .addSourceLines(
                        "ImmutablePerson.java",
                        "import java.util.Objects;",
                        "import java.util.Optional;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Bug\", generator = \"Immutables\")",
                        "final class ImmutableBug implements Bug {",
                        "    private final @Nullable String author;",
                        "    private ImmutablePerson(@Nullable String author) {",
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
                        "        public Person build() {",
                        "            return new ImmutableBug(author);",
                        "        }",
                        "    }",
                        "}");
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
                        "    int currentAge();",
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
                        "    private final int currentAge;",
                        "    private final @Nullable String company;",
                        "    private ImmutablePerson(String name, int currentAge, @Nullable String company) {",
                        "        this.name = name; this.currentAge = currentAge; this.company = company;",
                        "    }",
                        "    @Override",
                        "    public String name() { return name; }",
                        "    @Override",
                        "    public int currentAge() { return currentAge; }",
                        "    @Override",
                        "    public Optional<String> company() { return Optional.ofNullable(company); }",
                        "",
                        "    @Generated(from = \"Person\", generator = \"Immutables\")",
                        "    public static class Builder {",
                        "        private static final long INIT_BIT_NAME = 0x1L;",
                        "        private static final long INIT_BIT_CURRENT_AGE = 0x2L;",
                        "        private long initBits = 0x3L;",
                        "        private @Nullable String name;",
                        "        private @Nullable Integer currentAge;",
                        "        private @Nullable String company;",
                        "        public final Builder name(String name) {",
                        "            this.name = Objects.requireNonNull(name, \"name\");",
                        "            initBits &= ~INIT_BIT_NAME;",
                        "            return this;",
                        "        }",
                        "        public final Builder currentAge(int currentAge) {",
                        "            this.currentAge = currentAge;",
                        "            initBits &= ~INIT_BIT_CURRENT_AGE;",
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
                        "            currentAge(instance.currentAge());",
                        "            company(instance.company());",
                        "            return this;",
                        "        }",
                        "        public Person build() {",
                        "            if (initBits != 0) { throw new IllegalStateException(); }",
                        "            return new ImmutablePerson(name, currentAge, company);",
                        "        }",
                        "    }",
                        "}");
    }
}
