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

public class DeprecatedApiUsageTest {

    @Test
    public void testNotDeprecatedMethodCall() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

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
    public void testDeprecatedMethodCall() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}

                          @Deprecated(forRemoval = true)
                          public void deprecatedForRemovalMethod() {}

                          @Deprecated(forRemoval = false)
                          public void deprecatedNotForRemovalMethod() {}
                        }

                        class Test {
                          public void fun() {
                            // BUG: Diagnostic contains: Helper#deprecatedMethod is deprecated
                            new Helper().deprecatedMethod();
                            // BUG: Diagnostic contains: Helper#deprecatedForRemovalMethod is deprecated
                            new Helper().deprecatedForRemovalMethod();
                            // BUG: Diagnostic contains: Helper#deprecatedNotForRemovalMethod is deprecated
                            new Helper().deprecatedNotForRemovalMethod();
                          }
                        }
                        """)
                .doTest();
    }

    @Test
    // Error-prone wants to inline the Deprecated annotation, which looks worse
    @SuppressWarnings("MisformattedTestData")
    public void testDeprecatedFieldAccess() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
                          public static final String DEPRECATED_CONSTANT = "constant";

                          @Deprecated
                          public final String deprecatedField = "deprecated";
                        }

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
    public void testSuppressThroughCheckName() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }

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
    public void testSuppressThroughJavaCompilerDeprecation() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }

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
    public void testSuppressThroughJavaCompilerRemoval() {
        helper().addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
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
    public void testSuppressCombinedWithCompilerDeprecationFlag() {
        helper().setArgs("-Werror", "-Xlint:deprecation")
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

                        class Helper {
                          @Deprecated
                          public void deprecatedMethod() {}
                        }

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
    public void testSuppressRemovalCombinedWithCompilerDeprecationFlag() {
        helper().setArgs("-Werror", "-Xlint:deprecation")
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
                        import com.palantir.logsafe.*;
                        import java.util.function.*;
                        import java.util.*;

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

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DeprecatedApiUsage.class, getClass());
    }
}
