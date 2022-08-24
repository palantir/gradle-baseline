/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ForbidJavaxParameterTypeTest {

    @Test
    void failsIfIncorrectTypeIsSuppliedViaConstructor() {

        helper().addSourceLines(
                        "Test.java",
                        "package test;",
                        "import javax.ws.rs.Path;",
                        "import javax.ws.rs.GET;",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "class Test {",
                        "  public void requiresJakarta(@ForbidJavax Object o) {}",
                        "  ",
                        "  public static class Inner {",
                        "    @Path(\"foo\")",
                        "    @GET",
                        "    public String doGet() { return \"hi\"; }",
                        "  }",
                        "  public static void main(String[] args) {",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    new Test().requiresJakarta(new Inner());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void failsIfIncorrectTypeIsSuppliedViaParameter() {

        helper().addSourceLines(
                        "Test.java",
                        "package test;",
                        "import javax.ws.rs.Path;",
                        "import javax.ws.rs.GET;",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "class Test {",
                        "  public void requiresJakarta(@ForbidJavax Object o) {}",
                        "  ",
                        "  public static class Inner {",
                        "    @Path(\"foo\")",
                        "    @GET",
                        "    public String doGet() { return \"hi\"; }",
                        "  }",
                        "  public static void main(String[] args) {",
                        "    Inner inner = new Inner();",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    new Test().requiresJakarta(inner);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void failsIfIncorrectTypeIsSuppliedViaMethodReturn() {

        helper().addSourceLines(
                        "Test.java",
                        "package test;",
                        "import javax.ws.rs.Path;",
                        "import javax.ws.rs.GET;",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "class Test {",
                        "  public void requiresJakarta(@ForbidJavax Object o) {}",
                        "  ",
                        "  public static class Inner {",
                        "    @Path(\"foo\")",
                        "    @GET",
                        "    public String doGet() { return \"hi\"; }",
                        "  }",
                        "  public static Inner getInner() { return new Inner(); }",
                        "  public static void main(String[] args) {",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    new Test().requiresJakarta(getInner());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void stillFailsIfInnerIsSeparateClass() {
        helper().addSourceLines(
                        "Outer.java",
                        "import javax.ws.rs.Path;",
                        "import javax.ws.rs.GET;",
                        "  public class Outer {",
                        "    @Path(\"foo\")",
                        "    @GET",
                        "    public String doGet() { return \"hi\"; }",
                        "  }")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "class Test {",
                        "  public void requiresJakarta(@ForbidJavax Object o) {}",
                        "  ",
                        "  public static Outer getOuter() { return new Outer(); }",
                        "  public static void main(String[] args) {",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    new Test().requiresJakarta(getOuter());",
                        "  }",
                        "}")
                .doTest();
    }

    public interface JaxrsAnnotatedIface {
        @Path("foo")
        @GET
        String doGet();
    }

    public static final class JaxrsAnnotatedClass implements JaxrsAnnotatedIface {
        @Override
        public String doGet() {
            return "PONG";
        }
    }

    @Test
    void stillFailsIfInnerIsSeparateJar() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "import " + JaxrsAnnotatedIface.class.getCanonicalName() + ';',
                        "class Test {",
                        "  public static void requiresJakarta(@ForbidJavax Object o) {}",
                        "  public static void f(JaxrsAnnotatedIface arg) {",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    requiresJakarta(arg);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void failsIfGivenImplementationResource() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "import " + JaxrsAnnotatedClass.class.getCanonicalName() + ';',
                        "class Test {",
                        "  public static void requiresJakarta(@ForbidJavax Object o) {}",
                        "  public static void f() {",
                        "    JaxrsAnnotatedClass jaxrs = new JaxrsAnnotatedClass();",
                        "    // BUG: Diagnostic contains: ForbidJavaxParameterType",
                        "    requiresJakarta(jaxrs);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void knowinglyFailsIfGivenBlankObject() {
        // we can really only do so much, if all we're given is a blank object, we can't
        // realistically handle this case, so at least have a test confirming this behavior
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "import " + JaxrsAnnotatedClass.class.getCanonicalName() + ';',
                        "class Test {",
                        "  public static void requiresJakarta(@ForbidJavax Object o) {}",
                        "  public static void f(Object jaxrs) {",
                        "    requiresJakarta(jaxrs);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    @Disabled("Unfortunately disabled because we can't have both annotation types on the classpath")
    void succeedsIfGivenJakartaType() {

        helper().addSourceLines(
                        "Test.java",
                        "package test;",
                        "import jakarta.ws.rs.Path;",
                        "import jakarta.ws.rs.GET;",
                        "import com.palantir.errorprone.ForbidJavax;",
                        "class Test {",
                        "  public void requiresJakarta(@ForbidJavax Object o) {}",
                        "  ",
                        "  public static class Inner {",
                        "    @Path(\"foo\")",
                        "    @GET",
                        "    public String doGet() { return \"hi\"; }",
                        "  }",
                        "  public static Inner getInner() { return new Inner(); }",
                        "  public static void main(String[] args) {",
                        "    new Test().requiresJakarta(getInner());",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ForbidJavaxParameterType.class, getClass());
    }
}
