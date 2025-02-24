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
    public void testUnsafeStatementLambda() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f(@Unsafe Object value) {
                            Supplier<@Safe Object> supplier =
                                () -> {
                                  // BUG: Diagnostic contains: Dangerous return value:
                                  // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                                  return value;
                                };
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testUnsafeStatementLambdaMultipleReturns() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f(int i, @Safe Object safeValue, @Unsafe Object unsafeValue) {
                            Supplier<@Safe Object> supplier =
                                () -> {
                                  if (i == 0) {
                                    // BUG: Diagnostic contains: Dangerous return value:
                                    // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                                    return unsafeValue;
                                  } else if (i > 0) {
                                    return safeValue;
                                  } else {
                                    // BUG: Diagnostic contains: Dangerous return value:
                                    // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                                    return unsafeValue;
                                  }
                                };
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testUnsafeExpressionLambdaIdentityResult() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            Supplier<@Safe Object> supplier = () -> value;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testUnsafeExpressionLambdaExpressionResult() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f(@Unsafe String value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            Supplier<@Safe Object> supplier = () -> value + value;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testFunctionalInterfaceWithVoidReturnType() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f() {
                            // void-returning lambdas should pass the checks fine
                            Consumer<@Safe Object> supplier = (value) -> {};
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testFunctionalInterfaceWithMultipleMethods() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.List;
                        import java.util.function.*;

                        @FunctionalInterface
                        interface MultiMethod<T> {
                          T get();

                          default List<T> getMany() {
                            return List.of(get());
                          }

                          static <T> MultiMethod<T> of(T value) {
                            return () -> value;
                          }
                        }

                        class Test {
                          static void f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            MultiMethod<@Safe Object> supplier = () -> value;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testUnsafeLambdaAsReturnType() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static Supplier<@Safe Object> f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            return () -> value;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void testUnsafeLambdaAsMethodArgument() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          static void f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            fun(() -> value);
                          }

                          static void fun(Supplier<@Safe Object> in) {}
                        }
                        """)
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }
}
