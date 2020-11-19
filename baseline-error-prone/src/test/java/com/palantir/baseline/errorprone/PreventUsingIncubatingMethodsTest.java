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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;

public class PreventUsingIncubatingMethodsTest {

    // We define our own simple conjure incubating annotation at the right path:
    private static final String[] ANNOTATION_DEFINITION =
            new String[] {"package com.palantir.conjure.java.lib.internal;", "public @interface Incubating {}"};

    // A simple service with one incubating method and one non-incubating method.
    private static final String[] SERVICE_DEFINITION = new String[] {
        "package com.palantir;",
        "import com.palantir.conjure.java.lib.internal.Incubating;",
        "public interface Service {",
        "@Incubating",
        "int test();",
        "int test2();",
        "}"
    };

    @Test
    public void testIncubatingMethod() {
        CompilationTestHelper.newInstance(PreventUsingIncubatingMethods.class, getClass())
                .addSourceLines("Incubating.java", ANNOTATION_DEFINITION)
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should avoid calling incubating methods",
                        "service.test();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testIncubatingMethodReference() {
        CompilationTestHelper.newInstance(PreventUsingIncubatingMethods.class, getClass())
                .addSourceLines("Incubating.java", ANNOTATION_DEFINITION)
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should avoid calling incubating methods",
                        "Supplier<Integer> supp = service::test;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testNonIncubatingMethod() {
        CompilationTestHelper.newInstance(PreventUsingIncubatingMethods.class, getClass())
                .addSourceLines("Incubating.java", ANNOTATION_DEFINITION)
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "int result = service.test2();",
                        "Supplier<Integer> supp = service::test2;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testSuppressedIncubatingMethod() {
        CompilationTestHelper.newInstance(PreventUsingIncubatingMethods.class, getClass())
                .addSourceLines("Incubating.java", ANNOTATION_DEFINITION)
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "@SuppressWarnings(\"PreventUsingIncubatingMethods\")",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.test();",
                        "}",
                        "}")
                .doTest();
    }
}
