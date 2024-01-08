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
import org.junit.jupiter.api.Test;

class IllegalSafeLoggingLambdaTest {

    @Test
    void testLambdaReferencesUnsafeExternalData() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  Runnable f(RuntimeException exception) {",
                        "    String message = exception.getMessage();",
                        "      // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    return () -> fun(message);",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    void testAnonymousClassReferencesUnsafeExternalData() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  Runnable f(RuntimeException exception) {",
                        "    String message = exception.getMessage();",
                        "    return new Runnable() {",
                        "      @Override public void run() {",
                        "        // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "        fun(message);",
                        "      }",
                        "    };",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    void testNestedAnonymousInLambdaUnsafeExternalData() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  Function<RuntimeException, Runnable> f() {",
                        "    return exception -> {",
                        "      String message = exception.getMessage();",
                        "      return new Runnable() {",
                        "        @Override public void run() {",
                        "          // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "          fun(message);",
                        "        }",
                        "      };",
                        "    };",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    void testOptionalMap() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(@Unsafe String value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(Optional.ofNullable(value).map(in -> in + in));",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    void testStreamMap() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "  void f(@Unsafe Stream<String> value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(value.map(in -> in + in));",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    void testFunctionSafeType() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Dangerous",
                        "  Function<@Unsafe String, @Safe String> func = in -> in;",
                        "}")
                .doTest();
    }

    @Test
    void testFunctionConsumesSafetyAnnotatedType_expression() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Dangerous",
                        "  Consumer<@Unsafe String> func = in -> fun(in);",
                        "  void fun(@Safe Object ob) {}",
                        "}")
                .doTest();
    }

    @Test
    void testFunctionConsumesSafetyAnnotatedType_statement() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Dangerous",
                        "  Consumer<@Unsafe String> func = in -> { fun(in); };",
                        "  void fun(@Safe Object ob) {}",
                        "}")
                .doTest();
    }

    @Test
    void testFunctionConsumesSafetyAnnotatedType_reference() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Dangerous",
                        "  Consumer<@Unsafe String> func = Test::fun;",
                        "  void fun(@Safe Object ob) {}",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }
}
