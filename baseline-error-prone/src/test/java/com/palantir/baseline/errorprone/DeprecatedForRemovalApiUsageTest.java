/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

public class DeprecatedForRemovalApiUsageTest {

    @Test
    public void does_not_throw_on_non_deprecated_api_usage() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          public static final String CONSTANT = "not deprecated";
                          public String field = "not deprecated";

                          public static void notDeprecatedStaticMethod() {}

                          public void notDeprecatedMethod() {}
                        }

                        class Test {
                          public void fun() {
                            Helper.notDeprecatedStaticMethod();
                            System.out.println(Helper.CONSTANT);

                            Helper helper = new Helper();
                            helper.notDeprecatedMethod();
                            System.out.println(helper.field);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    // Error-prone wants to inline the Deprecated annotation, which looks worse
    @SuppressWarnings("MisformattedTestData")
    public void does_not_throw_on_deprecated_not_for_removal_api_usage() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public static final String CONSTANT = "not deprecated";
                          @Deprecated
                          public String field = "not deprecated";

                          @Deprecated
                          public static void notDeprecatedStaticMethod() {}

                          @Deprecated
                          public void notDeprecatedMethod() {}
                        }

                        class Test {
                          public void fun() {
                            Helper.notDeprecatedStaticMethod();
                            System.out.println(Helper.CONSTANT);

                            Helper helper = new Helper();
                            helper.notDeprecatedMethod();
                            System.out.println(helper.field);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void throws_on_deprecated_for_removal_method_usage() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedForRemovalMethod() {}
                        }

                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#deprecatedForRemovalMethod is deprecated for removal
                            new Helper().deprecatedForRemovalMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void check_message_contains_expected_check_name() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          public void fun() {
                            // We should be showing [removal] here rather than [DeprecatedForRemovalApiUsage]
                            //   to incentivize devs to use the backwards-compatible @SuppressWarnings("removal")
                            //   to manually suppress the check.
                            // BUG: Diagnostic contains: [removal] Helper#deprecatedMethod is deprecated for removal
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    // Error-prone wants to inline the Deprecated annotation, which looks worse
    @SuppressWarnings("MisformattedTestData")
    public void throws_on_deprecated_field_access() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public static final String DEPRECATED_CONSTANT = "constant";

                          @Deprecated(forRemoval = true)
                          public final String deprecatedField = "deprecated";
                        }

                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#DEPRECATED_CONSTANT is deprecated for removal
                            System.out.println(Helper.DEPRECATED_CONSTANT);
                            // BUG: Diagnostic contains: Helper#deprecatedField is deprecated for removal
                            System.out.println(new Helper().deprecatedField);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void can_suppress_through_check_name() {
        helper().setArgs("-Xlint:-removal")
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          @SuppressWarnings("DeprecatedForRemovalApiUsage")
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void can_suppress_through_java_compiler_deprecation_suppression() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          @SuppressWarnings("removal")
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void does_not_suppress_through_java_compiler_deprecation_suppression() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          // This should not work, because the error-prone check only targets regular deprecation
                          @SuppressWarnings("deprecation")
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#deprecatedMethod is deprecated for removal
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void can_suppress_through_deprecation_even_with_removal_compiler_flag_disabled() {
        helper().setArgs("-Xlint:-removal")
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          @SuppressWarnings("removal")
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void compiler_can_warn_with_deprecation_flag_even_with_check() {
        helper().setArgs("-Werror", "-Xlint:removal")
                // In this case, we check that the compiler is the one warning us
                .matchAllDiagnostics()
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated(forRemoval = true)
                          public void deprecatedMethod() {}
                        }

                        class Test {
                          public void fun() {
                            // This is the regular compiler warning, not our custom one
                            // Somehow it doesn't detect [deprecation] in the output, presumably because it's a
                            //   separate part of the compiler output.
                            // BUG: Diagnostic contains: deprecatedMethod() in Helper has been deprecated
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DeprecatedForRemovalApiUsage.class, getClass());
    }
}
