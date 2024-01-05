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

public class DeprecatedConjureTest {

    private static final String[] SERVICE_DEFINITION = new String[]{
            "package com.palantir;",
            "import com.palantir.conjure.java.lib.internal.ClientEndpoint;",
            "public interface Service {",

            "int unannotatedMethod();",

            "@Deprecated(forRemoval = true)",
            "@ClientEndpoint(method = \"GET\", path = \"/deprecatedForRemovalConjureEndpoint\")",
            "int deprecatedForRemovalConjureEndpoint();",

            "@Deprecated",
            "@ClientEndpoint(method = \"GET\", path = \"/deprecatedConjureEndpoint\")",
            "int deprecatedConjureEndpoint();",

            "@ClientEndpoint(method = \"GET\", path = \"/nonDeprecatedConjure\")",
            "int nonDeprecatedConjure();",

            "@Deprecated(forRemoval = true)",
            "int deprecatedForRemovalNotConjure();",

            "@Deprecated",
            "int deprecatedNotConjure();",
            "}"
    };

    @Test
    public void testDeprecatedForRemovalConjure() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should not use Conjure endpoints that are marked for removal as that may block",
                        "service.deprecatedForRemovalConjureEndpoint();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testDeprecatedForRemovalNotConjure() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.deprecatedForRemovalNotConjure();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testPlainDeprecatedConjure() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.deprecatedConjureEndpoint();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testPlainMethod() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.unannotatedMethod();",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testDeprecatedConjureReference() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "// BUG: Diagnostic contains: You should not use Conjure endpoints that are marked for removal as that may block",
                        "Supplier<Integer> supp = service::deprecatedForRemovalConjureEndpoint;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testDeprecatedForRemovalConjureInsideMethod() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "import com.palantir.conjure.java.lib.internal.Incubating;",
                        "public final class Main {",
                        "@Incubating",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "int result = service.deprecatedForRemovalConjureEndpoint();",
                        "Supplier<Integer> supp = service::deprecatedForRemovalConjureEndpoint;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testNonDeprecatedConjure() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "import java.util.function.Supplier;",
                        "public final class Main {",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "int result = service.nonDeprecatedConjure();",
                        "Supplier<Integer> supp = service::nonDeprecatedConjure;",
                        "}",
                        "}")
                .doTest();
    }

    @Test
    public void testSuppressedDeprecatedConjure() {
        CompilationTestHelper.newInstance(DeprecatedConjure.class, getClass())
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines(
                        "Main.java",
                        "package com.palantir;",
                        "public final class Main {",
                        "@SuppressWarnings(\"DeprecatedConjure\")",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.deprecatedForRemovalConjureEndpoint();",
                        "}",
                        "}")
                .doTest();
    }
}
