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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConjureEndpointDeprecatedForRemovalTest {

    private static final String[] SERVICE_DEFINITION = new String[] {
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
        buildStandardTestHelper(
                        "// BUG: Diagnostic contains: You should not use Conjure endpoints that are marked for removal",
                        "service.deprecatedForRemovalConjureEndpoint();")
                .doTest();
    }

    @Test
    public void testDeprecatedForRemovalNotConjure() {
        buildStandardTestHelper(
                        "service.deprecatedForRemovalNotConjure();",
                        "Supplier<Integer> supp = service::deprecatedForRemovalNotConjure;")
                .doTest();
    }

    @Test
    public void testPlainDeprecatedConjure() {
        buildStandardTestHelper(
                        "service.deprecatedConjureEndpoint();",
                        "Supplier<Integer> supp = service::deprecatedConjureEndpoint;")
                .doTest();
    }

    @Test
    public void testPlainMethod() {
        buildStandardTestHelper("service.unannotatedMethod();", "Supplier<Integer> supp = service::unannotatedMethod;")
                .doTest();
    }

    @Test
    public void testDeprecatedForRemovalConjureReference() {
        buildStandardTestHelper(
                        "// BUG: Diagnostic contains: You should not use Conjure endpoints that are marked for removal",
                        "Supplier<Integer> supp = service::deprecatedForRemovalConjureEndpoint;")
                .doTest();
    }

    @Test
    public void testNonDeprecatedConjure() {
        buildStandardTestHelper(
                        "int result = service.nonDeprecatedConjure();",
                        "Supplier<Integer> supp = service::nonDeprecatedConjure;")
                .doTest();
    }

    @Test
    public void testSuppressedDeprecatedConjure() {
        buildTestHelper(buildMain(
                        "public final class Main {",
                        "@SuppressWarnings(\"ConjureEndpointDeprecatedForRemoval\")",
                        "public static void main(String[] args) {",
                        "Service service = null;",
                        "service.deprecatedForRemovalConjureEndpoint();"))
                .doTest();
    }

    private CompilationTestHelper buildStandardTestHelper(String... testLines) {
        return buildTestHelper(buildStandardMain(testLines));
    }

    private CompilationTestHelper buildTestHelper(String... mainContents) {
        return CompilationTestHelper.newInstance(ConjureEndpointDeprecatedForRemoval.class, getClass())
                // do not issue warning for the deprecated for removal annotation because it messes up other checks
                .setArgs("-Xlint:-removal")
                .addSourceLines("Service.java", SERVICE_DEFINITION)
                .addSourceLines("Main.java", mainContents);
    }

    /**
     * Creates contents of the Main.java class, inserting the given test lines at the end of the main method.
     */
    private static String[] buildStandardMain(String... testLines) {
        List<String> result = new ArrayList<>();

        result.add("package com.palantir;");
        result.add("import java.util.function.Supplier;");
        result.add("public final class Main {");
        result.add("public static void main(String[] args) {");
        result.add("Service service = null;");
        for (String line : testLines) {
            result.add(line);
        }
        result.add("}");
        result.add("}");
        return result.toArray(new String[] {});
    }

    /**
     * Creates contents of the Main.java class, putting it in the com.palantir package and closing off the main method
     * and class definition.
     */
    private static String[] buildMain(String... testLines) {
        List<String> result = new ArrayList<>();

        result.add("package com.palantir;");
        for (String line : testLines) {
            result.add(line);
        }
        // closing lines
        result.add("}");
        result.add("}");

        return result.toArray(new String[] {});
    }
}
