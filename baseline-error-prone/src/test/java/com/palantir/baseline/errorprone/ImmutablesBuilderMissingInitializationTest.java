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
    public void testPass() {
        helper().addSourceLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Person {",
                        "    String name();",
                        "    static ImmutablePerson.Builder builder() {",
                        "        return new ImmutablePerson.Builder();",
                        "    }",
                        "}")
                .addSourceLines(
                        "ImmutablePerson.java",
                        "import java.util.Objects;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Person\", generator = \"Immutables\")",
                        "final class ImmutablePerson implements Person {",
                        "    private final String name;",
                        "    private ImmutablePerson(String name) { this.name = name; }",
                        "    @Override",
                        "    public String name() { return name; }",
                        "",
                        "    @Generated(from = \"Person\", generator = \"Immutables\")",
                        "    public static final class Builder {",
                        "        private static final long INIT_BIT_NAME = 0x1L;",
                        "        private long initBits = 0x1L;",
                        "        private @Nullable String name;",
                        "        public final Builder name(String name) {",
                        "            this.name = Objects.requireNonNull(name, \"name\");",
                        "            initBits &= ~INIT_BIT_NAME;",
                        "            return this;",
                        "        }",
                        "        public Person build() {",
                        "            return new ImmutablePerson(name);",
                        "        }",
                        "    }",
                        "}")
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        Person.builder().name(\"name\").build();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testFail() {
        helper().addSourceLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "public interface Person {",
                        "    String name();",
                        "    static ImmutablePerson.Builder builder() {",
                        "        return new ImmutablePerson.Builder();",
                        "    }",
                        "}")
                .addSourceLines(
                        "ImmutablePerson.java",
                        "import java.util.Objects;",
                        "import javax.annotation.Nullable;",
                        "import org.immutables.value.Generated;",
                        "@Generated(from = \"Person\", generator = \"Immutables\")",
                        "final class ImmutablePerson implements Person {",
                        "    private final String name;",
                        "    private ImmutablePerson(String name) { this.name = name; }",
                        "    @Override",
                        "    public String name() { return name; }",
                        "",
                        "    @Generated(from = \"Person\", generator = \"Immutables\")",
                        "    public static final class Builder {",
                        "        private static final long INIT_BIT_NAME = 0x1L;",
                        "        private long initBits = 0x1L;",
                        "        private @Nullable String name;",
                        "        public final Builder name(String name) {",
                        "            this.name = Objects.requireNonNull(name, \"name\");",
                        "            initBits &= ~INIT_BIT_NAME;",
                        "            return this;",
                        "        }",
                        "        public Person build() {",
                        "            return new ImmutablePerson(name);",
                        "        }",
                        "    }",
                        "}")
                .addSourceLines(
                        "MyTest.java",
                        "public class MyTest {",
                        "    public static void main(String[] args) {",
                        "        // BUG: Diagnostic contains: Some builder fields have not been initialized: name",
                        "        Person.builder().build();",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesBuilderMissingInitialization.class, getClass());
    }
}
