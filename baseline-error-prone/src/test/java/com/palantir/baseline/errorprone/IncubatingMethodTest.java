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
import org.junit.jupiter.api.Test;

public class IncubatingMethodTest {

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
        CompilationTestHelper.newInstance(IncubatingMethod.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should avoid using incubating methods",
                        "service.test();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testIncubatingMethodReference() {
        CompilationTestHelper.newInstance(IncubatingMethod.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should avoid using incubating methods",
                        "Supplier<Integer> supp = service::test;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testNonIncubatingMethod() {
        CompilationTestHelper.newInstance(IncubatingMethod.class, getClass())
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
        CompilationTestHelper.newInstance(IncubatingMethod.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "@SuppressWarnings(\"IncubatingMethod\")",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.test();",
                        "}",
                        "}")
                .doTest();
    }
}
