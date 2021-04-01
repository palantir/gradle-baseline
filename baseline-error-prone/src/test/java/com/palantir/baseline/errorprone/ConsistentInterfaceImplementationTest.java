/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

class ConsistentInterfaceImplementationTest {

    @Test
    void swapped_names() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    // BUG: Diagnostic contains: ConsistentInterfaceImplementation",
                        "    public void doStuff(String bar, String foo) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void swapped_across_types() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar, int baz);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String baz, String bar, int foo) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void overridden_name() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String baz) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void single_variable() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String bar) {}",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ConsistentInterfaceImplementation.class, getClass());
    }
}
