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

import com.google.common.collect.Lists;
import com.google.errorprone.CompilationTestHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RawTypesTest {

    @Test
    void testVariableDeclaration() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        // BUG: Diagnostic contains: Avoid raw types",
                        "        ArrayList list = new ArrayList<String>();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testRawParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        // BUG: Diagnostic contains: Avoid raw types",
                        "        ArrayList<ArrayList> list = new ArrayList<>();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testFieldDeclaration() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Avoid raw types",
                        "    ArrayList list;",
                        "}")
                .doTest();
    }

    @Test
    void testMethodArgument() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Avoid raw types",
                        "    int f(ArrayList list) {",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testReturnType() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Avoid raw types",
                        "    ArrayList f() {",
                        "        return new ArrayList<String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testClassInstantiation() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        // BUG: Diagnostic contains: Avoid raw types",
                        "        ArrayList<String> list = new ArrayList();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testCast1() {
        helper().addSourceLines(
                "Test.java",
                "import " + ArrayList.class.getName() + ";",
                "class Test {",
                "    int f() {",
                "        ArrayList<String> list = new ArrayList<String>();",
                "        // BUG: Diagnostic contains: Avoid raw types",
                "        Object obj = (ArrayList) list;",
                "        return obj.hashCode();",
                "    }",
                "}")
                .doTest();
    }

    @Test
    void testCast2() {
        helper().addSourceLines(
                "Test.java",
                "import " + ArrayList.class.getName() + ";",
                "class Test {",
                "    int f() {",
                "        ArrayList<String> list = new ArrayList<String>();",
                "        // BUG: Diagnostic contains: Avoid raw types",
                "        ArrayList<Integer> list2 = (ArrayList<Integer>) (ArrayList) list;",
                "        return list2.hashCode();",
                "    }",
                "}")
                .doTest();
    }

    @Test
    void testExtends() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "// BUG: Diagnostic contains: Avoid raw types",
                        "class MyList extends ArrayList {",
                        "}")
                .doTest();
    }

    @Test
    void testImplements1() {
        helper().addSourceLines(
                        "Test.java",
                        "interface A<T> {}",
                        "// BUG: Diagnostic contains: Avoid raw types",
                        "class MyClass implements A {",
                        "}")
                .doTest();
    }

    @Test
    void testImplements2() {
        helper().addSourceLines(
                        "Test.java",
                        "interface A {}",
                        "interface B<T> {}",
                        "// BUG: Diagnostic contains: Avoid raw types",
                        "class MyClass implements A, B {",
                        "}")
                .doTest();
    }

    @Test
    void testImplements3() {
        helper().addSourceLines(
                        "Test.java",
                        "interface A<T> {}",
                        "interface B<T> {}",
                        "// BUG: Diagnostic contains: Avoid raw types",
                        "class MyClass implements A, B {",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeExplicitParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "import " + Lists.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        ArrayList<String> list = new ArrayList<String>();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeDiamond() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "import " + Lists.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        ArrayList<String> list = new ArrayList<>();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeInnerParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    int f() {",
                        "        ArrayList<ArrayList<String>> list = new ArrayList<>();",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeField() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    ArrayList<String> list;",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeMethodArgument() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    int f(ArrayList<String> list) {",
                        "        return list.size();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeReturnType() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class Test {",
                        "    ArrayList<String> f() {",
                        "        return new ArrayList<String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeCast() {
        helper().addSourceLines(
                "Test.java",
                "import " + ArrayList.class.getName() + ";",
                "import " + List.class.getName() + ";",
                "class Test {",
                "    int f() {",
                "        List<String> list = new ArrayList<String>();",
                "        ArrayList<String> list2 = (ArrayList<String>) list;",
                "        return list2.hashCode();",
                "    }",
                "}")
                .doTest();
    }

    @Test
    void testNegativeExtends() {
        helper().addSourceLines(
                        "Test.java",
                        "import " + ArrayList.class.getName() + ";",
                        "class MyList extends ArrayList<String> {",
                        "}")
                .doTest();
    }

    @Test
    void testNegativeImplements3() {
        helper().addSourceLines(
                        "Test.java",
                        "interface A<T> {}",
                        "interface B<T> {}",
                        "class MyClass implements A<String>, B<Integer> {",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(RawTypes.class, getClass());
    }
}
