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

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistentOverridesTest {

    @Test
    void ignores_generic_methods() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  interface ParameterizedInterface<T> {",
                        "    void doStuff(T foo, T bar);",
                        "  }",
                        "  interface ParameterizedMethod {",
                        "    <T> void otherStuff(T foo, T bar);",
                        "  }",
                        "  class DefaultFoo implements ParameterizedInterface<String>, ParameterizedMethod {",
                        "    @Override",
                        "    public void doStuff(String bar, String foo) {}",
                        "    @Override",
                        "    public <T> void otherStuff(T bar, T foo) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void ignores_unambiguous_rename() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, int bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String bar, int foo) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void ignores_unambiguous_generics_rename() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(List<String> foo, List<Integer> bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(List<String> bar, List<Integer> foo) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void ignores_unhelpfulNames() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String a, String b);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String bar) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void allows_unused_variables() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String _bar) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void allows_unused_variable_names() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String _baz) {}",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String _bar) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void allows_unused_variables_to_be_used() {
        fix().addInputLines(
                        "Test.java",
                        "import " + List.class.getCanonicalName() + ";",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String _bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String bar) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void swapped_names() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    boolean doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public boolean doStuff(String bar, String foo) {",
                        "      return bar.equals(foo);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    boolean doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public boolean doStuff(String foo, String bar) {",
                        "      return foo.equals(bar);",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void overridden_name() {
        fix().addInputLines(
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
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public void doStuff(String foo, String bar) {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void extended_interface() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  interface Bar extends Foo {",
                        "    void doStuff(String foo, String baz);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "  interface Bar extends Foo {",
                        "    void doStuff(String foo, String bar);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void interface_has_one_meaningless_name() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    boolean doStuff(String a, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public boolean doStuff(String bang, String foo) {",
                        "      return bang.equals(foo);",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  interface Foo {",
                        "    boolean doStuff(String a, String bar);",
                        "  }",
                        "  class DefaultFoo implements Foo {",
                        "    @Override",
                        "    public boolean doStuff(String bang, String bar) {",
                        "      return bang.equals(bar);",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ConsistentOverrides(), getClass());
    }
}
