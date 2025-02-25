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

    @Test
    public void testUnsafeOptionalLambdaReturn() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        interface MaybeSupplier<T> {
                          Optional<T> get();
                        }

                        class Test {
                          static MaybeSupplier<@Safe Object> f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous return value:
                            // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                            return () -> Optional.of(value);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testFunctionSafeType() {
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
                            Function<@Unsafe String, @Safe String> func = in -> in;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testFunctionalInterfaceSafeType() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        interface F<T, U, V> {
                          V apply(T t, U u);
                        }

                        class Test {
                          static void f(@Unsafe Object value) {
                            F<@Unsafe String, @Safe String, @Safe String> func =
                                (s1, s2) -> {
                                  if (s1 == null) {
                                    // This is safe
                                    return s2;
                                  } else {
                                    // BUG: Diagnostic contains: Dangerous return value:
                                    // result is 'UNSAFE' but the lambda expects return 'SAFE'.
                                    return s1;
                                  }
                                };
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testLambdaConsumesSafetyAnnotatedType_expression() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          // BUG: Diagnostic contains: Dangerous argument value:
                          // arg is 'UNSAFE' but the parameter requires 'SAFE'.
                          Consumer<@Unsafe String> func = in -> fun(in);

                          void fun(@Safe Object ob) {}
                        }
                        """)
                .doTest();
    }

    @Test
    void testLambdaConsumesSafetyAnnotatedType_statement() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          Consumer<@Unsafe String> func =
                              in -> {
                                // BUG: Diagnostic contains: Dangerous argument value:
                                // arg is 'UNSAFE' but the parameter requires 'SAFE'.
                                fun(in);
                              };

                          void fun(@Safe Object ob) {}
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceParameterType() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // method reference expects argument 0 with safety 'SAFE', but will be passed 'UNSAFE'
                          Consumer<@Unsafe String> func = this::fun;

                          void fun(@Safe Object ob) {}
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceAsMethodReturn() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          void fun(@Safe Object ob) {}

                          Consumer<@Unsafe String> getFunc() {
                            // BUG: Diagnostic contains: Dangerous method reference:
                            // method reference expects argument 0 with safety 'SAFE', but will be passed 'UNSAFE'
                            return this::fun;
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceAsMethodArgument() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          void fun(@Safe Object ob) {}

                          void callFunc(Consumer<@Unsafe String> func) {}

                          void f(@Unsafe Object value) {
                            // BUG: Diagnostic contains: Dangerous method reference:
                            // method reference expects argument 0 with safety 'SAFE', but will be passed 'UNSAFE'
                            callFunc(this::fun);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceMultipleParameters() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // method reference expects argument 1 with safety 'SAFE', but will be passed 'UNSAFE'
                          BiConsumer<@Unsafe String, @Unsafe String> func = this::fun;

                          // BUG: Diagnostic contains: Dangerous method reference:
                          // method reference expects argument 1 with safety 'SAFE', but will be passed 'UNSAFE'
                          BiConsumer<@Safe String, @Unsafe String> func2 = this::fun;

                          // This is fine
                          BiConsumer<@Safe String, @Safe String> func3 = this::fun;

                          void fun(@Unsafe Object ob, @Safe Object ob2) {}
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceMethodAnnotation() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        class Test {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // expected return type 'SAFE' but the reference returns 'UNSAFE'.
                          Supplier<@Safe String> func = this::fun;

                          @Unsafe
                          String fun() {
                            return "unsafe";
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceReturnType() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        @Unsafe
                        class MyObject {}

                        class Test {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // expected return type 'SAFE' but the reference returns 'UNSAFE'.
                          Supplier<@Safe MyObject> func = this::fun;

                          MyObject fun() {
                            return new MyObject();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceInheritedSafety() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        interface MyInterface {
                          @Unsafe
                          String fun();
                        }

                        class Test implements MyInterface {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // expected return type 'SAFE' but the reference returns 'UNSAFE'.
                          Supplier<@Safe String> func = this::fun;

                          public String fun() {
                            return "unsafe";
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    void testMemberReferenceInheritedDiamondSafety() {
        // Note: Diamond inheritance with conflicting safety types is prevented and covered
        //   by IllegalSafeLoggingArgumentTest#testDiamondMethodSafetyInheritance
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;

                        interface MyInterface {
                          @Unsafe
                          String fun();
                        }

                        interface MySafeInterface extends MyInterface {
                          String fun();
                        }

                        interface MyUnsafeInterface extends MyInterface {
                          String fun();
                        }

                        class Test implements MySafeInterface, MyUnsafeInterface {
                          // BUG: Diagnostic contains: Dangerous method reference:
                          // expected return type 'SAFE' but the reference returns 'UNSAFE'.
                          Supplier<@Safe String> func = this::fun;

                          public String fun() {
                            return "unsafe";
                          }
                        }
                        """)
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }
}
