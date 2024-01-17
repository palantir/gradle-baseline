/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

final class ResourceIdentifierGetEqualsUsageTest {
    @Test
    void testHasInstance() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasInstance(\"test\");",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();

        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.getInstance().equals(\"test\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasInstance(\"test\");",
                        "  }",
                        "}")
                .doTest();

        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return \"test\".equals(rid.getInstance());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasInstance(\"test\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testHasLocator() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasLocator(\"test\");",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();

        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.getLocator().equals(\"test\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasLocator(\"test\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testHasService() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasService(\"test\");",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();

        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.getService().equals(\"test\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasService(\"test\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testHasType() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasType(\"test\");",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();

        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.getType().equals(\"test\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "public class Test {",
                        "  boolean f(ResourceIdentifier rid) {",
                        "    return rid.hasType(\"test\");",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(ResourceIdentifierGetEqualsUsage.class, getClass());
    }
}
