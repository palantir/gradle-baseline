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
import com.google.errorprone.scanner.ScannerSupplier;
import org.junit.jupiter.api.Test;

public class DeprecatedApiUsageTest {

    @Test
    public void does_not_throw_on_non_deprecated_api_usage() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          public static final String CONSTANT = "not deprecated";
                          public String field = "not deprecated";

                          public static void notDeprecatedStaticMethod() {}

                          public void notDeprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
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
    public void throws_on_deprecated_method_usage() {
        // Explicitly disable -Xlint:-removal to ensure we only catch regular deprecations
        helper().setArgs("-Xlint:-removal")
                .addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}

                          @Deprecated(forRemoval = true)
                          public void deprecatedForRemovalMethod() {}

                          @Deprecated(forRemoval = false)
                          public void deprecatedNotForRemovalMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#deprecatedMethod is deprecated
                            new Helper().deprecatedMethod();
                            // This should NOT be flagged, as it's deprecated but for removal, which is caught
                            //   by the compiler with -Xlint:removal -Werror
                            new Helper().deprecatedForRemovalMethod();
                            // BUG: Diagnostic contains: Helper#deprecatedNotForRemovalMethod is deprecated
                            new Helper().deprecatedNotForRemovalMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void check_message_contains_expected_check_name() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          public void fun() {
                            // We should be showing [deprecation] here rather than [DeprecatedApiUsage]
                            //   to incentivize devs to use the backwards-compatible @SuppressWarnings("deprecation")
                            //   to manually suppress the check.
                            // BUG: Diagnostic contains: [deprecation] Helper#deprecatedMethod is deprecated
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
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public static final String DEPRECATED_CONSTANT = "constant";

                          @Deprecated
                          public final String deprecatedField = "deprecated";
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#DEPRECATED_CONSTANT is deprecated
                            System.out.println(Helper.DEPRECATED_CONSTANT);
                            // BUG: Diagnostic contains: Helper#deprecatedField is deprecated
                            System.out.println(new Helper().deprecatedField);
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void can_suppress_through_check_name() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          @SuppressWarnings("DeprecatedApiUsage")
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
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          @SuppressWarnings("deprecation")
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void does_not_suppress_through_java_compiler_removal_suppression() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          // This should not work, because the error-prone check only targets regular deprecation
                          @SuppressWarnings("removal")
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#deprecatedMethod is deprecated
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void can_suppress_through_deprecation_even_with_deprecation_compiler_flag() {
        helper().setArgs("-Werror", "-Xlint:deprecation")
                .addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          @SuppressWarnings("deprecation")
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void compiler_can_warn_with_deprecation_flag_even_with_check() {
        helper().setArgs("-Werror", "-Xlint:deprecation")
                // In this case, we check that the compiler is the one warning us
                .matchAllDiagnostics()
                .addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
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

    @Test
    public void warns_on_deprecation_from_other_class_with_same_simple_name() {
        helper().addSourceLines(
                        "com/Test.java", // Note: different package
                        // language=Java
                        """
                        package com;

                        public class Test {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: com.Test#deprecatedMethod is deprecated
                            (new com.Test()).deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    // Error-prone wants to inline the Deprecated annotation, which looks worse
    @SuppressWarnings("MisformattedTestData")
    public void do_not_warn_on_import_statements() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        package com;

                        @Deprecated
                        public class Helper {}
                        """)
                .addSourceLines(
                        "Parent.java",
                        // language=Java
                        """
                        package com;

                        public class Parent {
                            @Deprecated
                            public static String CONSTANT = "constant";

                            @Deprecated
                            public static class Nested {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.Helper;
                        import com.Parent.Nested;
                        import static com.Parent.CONSTANT;

                        class Test {}
                        """)
                .doTest();
    }

    @Test
    public void throws_on_deprecated_class_usage() {
        helper().addSourceLines(
                        "app/Helper.java",
                        // language=Java
                        """
                        package app;

                        @Deprecated
                        public class Helper {}
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        // This import should not get flagged
                        import app.Helper;
                        import java.util.stream.Stream;

                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper is deprecated
                            Stream.of(new Helper())
                                // BUG: Diagnostic contains: Helper is deprecated
                                .forEach((Helper c) -> c.toString());
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    // Error-prone wants to inline the Deprecated annotation, which looks worse
    @SuppressWarnings("MisformattedTestData")
    public void throws_on_deprecated_nested_class_usage() {
        helper().addSourceLines(
                        "app/Helper.java",
                        // language=Java
                        """
                        package app;

                        public class Helper {
                            @Deprecated
                            public static class Nested {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        // This import should not get flagged
                        import app.Helper;
                        import java.util.stream.Stream;

                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper.Nested is deprecated
                            Stream.of(new Helper.Nested())
                                // BUG: Diagnostic contains: Helper.Nested is deprecated
                                .forEach((Helper.Nested c) -> c.toString());
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void compiler_allows_deprecations_within_deprecated_methods() {
        // Using a raw compilation helper here to verify the compiler behavior, rather than the error-prone check
        CompilationTestHelper.newInstance(ScannerSupplier.fromBugCheckerClasses(), getClass())
                .setArgs("-Xlint:deprecation", "-Werror")
                .addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          @Deprecated
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void error_prone_check_allows_deprecations_within_deprecated_methods() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        class Test {
                          @Deprecated
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void compiler_allows_deprecations_within_deprecated_classes() {
        // Using a raw compilation helper here to verify the compiler behavior, rather than the error-prone check
        CompilationTestHelper.newInstance(ScannerSupplier.fromBugCheckerClasses(), getClass())
                .setArgs("-Xlint:deprecation", "-Werror")
                .addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        @Deprecated
                        class Test {
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void error_prone_check_allows_deprecations_within_deprecated_classes() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        @Deprecated
                        class Test {
                          public void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    public void error_prone_check_allows_deprecations_within_deprecated_interfaces() {
        helper().addSourceLines(
                        "Helper.java",
                        // language=Java
                        """
                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }
                        """)
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        @Deprecated
                        interface Test {
                          default void fun() {
                            new Helper().deprecatedMethod();
                          }
                        }
                        """)
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DeprecatedApiUsage.class, getClass());
    }
}
