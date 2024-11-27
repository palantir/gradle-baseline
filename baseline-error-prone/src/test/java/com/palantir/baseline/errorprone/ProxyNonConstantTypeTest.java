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

class ProxyNonConstantTypeTest {

    @Test
    void testGuavaReflectionNewProxy() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.common.reflect.Reflection;",
                        "class Test {",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: proxy",
                        "    Reflection.newProxy(Test.class, null);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testGuavaReflectionNewProxySuppression() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.reflect.Reflection;",
                        "class Test {",
                        "  void f() {",
                        "    Reflection.newProxy(Test.class, null);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.reflect.Reflection;",
                        "class Test {",
                        "  @SuppressWarnings(\"ProxyNonConstantType\")",
                        "  void f() {",
                        "    Reflection.newProxy(Test.class, null);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testConstantInterfacesInline() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.Proxy;",
                        "class Test {",
                        "  void f() {",
                        "    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Test.class}, null);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testConstantInterfacesDynamic() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.Proxy;",
                        "class Test {",
                        "  void f(Class<?> iface) {",
                        "    // BUG: Diagnostic contains: proxy",
                        "    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{iface}, null);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testConstantInterfacesDynamicSuppression() {
        fix().addInputLines(
                        "Test.java",
                        "import java.lang.reflect.Proxy;",
                        "class Test {",
                        "  void f(Class<?> iface) {",
                        "    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{iface}, null);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.lang.reflect.Proxy;",
                        "class Test {",
                        "  @SuppressWarnings(\"ProxyNonConstantType\")",
                        "  void f(Class<?> iface) {",
                        "    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{iface}, null);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresTestCode() {
        helper().addSourceLines(
                        "Foo.java",
                        "import java.lang.reflect.Proxy;",
                        "import org.junit.Test;",
                        "class Foo {",
                        "  @Test",
                        "  public void test() {}",
                        "  void f(Class<?> iface) {",
                        "    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{iface}, null);",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ProxyNonConstantType.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(ProxyNonConstantType.class, getClass());
    }
}
