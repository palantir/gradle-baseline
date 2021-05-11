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

public class ImmutablesInterfaceDefaultValueTest {

    @Test
    public void testPassesWhenDefaultMethodAnnotatedValueDefault() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.immutables.value.*;",
                        "public class Test {",
                        "    @Value.Immutable",
                        "    public interface InterfaceWithValueDefault {",
                        "        String value();",
                        "        @Value.Default",
                        "        default String defaultValue() {",
                        "            return \"default\";",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenDefaultMethodAnnotatedValueDerived() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.immutables.value.*;",
                        "public class Test {",
                        "    @Value.Immutable",
                        "    public interface InterfaceWithValueDefault {",
                        "        String value();",
                        "        @Value.Derived",
                        "        default String derivedValue() {",
                        "            return value();",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassesWhenDefaultMethodAnnotatedValueLazy() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.immutables.value.*;",
                        "public class Test {",
                        "    @Value.Immutable",
                        "    public interface InterfaceWithValueDefault {",
                        "        String value();",
                        "        @Value.Lazy",
                        "        default String lazyValue() {",
                        "            return value();",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void refactorsMissingDefaultValueAnnotation() {
        refactoring()
                .addInputLines(
                        "Test.java",
                        "import org.immutables.value.*;",
                        "public class Test {",
                        "    @Value.Immutable",
                        "    public interface InterfaceWithValueDefault {",
                        "        String value();",
                        "    // BUG: Diagnostic contains: "
                                + "@Value.Immutable interface default methods should be annotated @Value.Default",
                        "        default String defaultValue() {",
                        "            return \"default\";",
                        "        }",
                        "        @Value.Derived",
                        "        default String derivedValue() {",
                        "            return value();",
                        "        }",
                        "        @Value.Lazy",
                        "        default String lazyValue() {",
                        "            return value();",
                        "        }",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.immutables.value.*;",
                        "public class Test {",
                        "    @Value.Immutable",
                        "    public interface InterfaceWithValueDefault {",
                        "        String value();",
                        "        @Value.Default",
                        "        default String defaultValue() {",
                        "            return \"default\";",
                        "        }",
                        "        @Value.Derived",
                        "        default String derivedValue() {",
                        "            return value();",
                        "        }",
                        "        @Value.Lazy",
                        "        default String lazyValue() {",
                        "            return value();",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesInterfaceDefaultValue.class, getClass());
    }

    private RefactoringValidator refactoring() {
        return RefactoringValidator.of(ImmutablesInterfaceDefaultValue.class, getClass());
    }
}
