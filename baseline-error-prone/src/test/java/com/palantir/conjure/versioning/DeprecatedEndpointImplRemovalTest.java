/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.versioning;

import com.palantir.baseline.errorprone.RefactoringValidator;
import org.junit.jupiter.api.Test;

public class DeprecatedEndpointImplRemovalTest {

    @Test
    public void testSingleEndpoint() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.lib.internal.ConjureServerEndpoint;",
                        "class Test {",
                        "  interface Iface {",
                        "    @ConjureServerEndpoint",
                        "    @Deprecated(forRemoval = true)",
                        "    void deprecatedForRemovalMethod();",
                        "  }",
                        "  static class Impl implements Iface {",
                        "    @Override",
                        "    public void deprecatedForRemovalMethod() {",
                        "      return;",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.conjure.RemoveLater;",
                        "import com.palantir.conjure.java.lib.internal.ConjureServerEndpoint;",
                        "class Test {",
                        "  interface Iface {",
                        "    @ConjureServerEndpoint",
                        "    @Deprecated(forRemoval = true)",
                        "    void deprecatedForRemovalMethod();",
                        "  }",
                        "  static class Impl implements Iface {",
                        "    @RemoveLater",
                        "    public void deprecatedForRemovalMethod() {",
                        "      return;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMultipleEndpoints() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.lib.internal.ConjureServerEndpoint;",
                        "class Test {",
                        "  interface Iface {",
                        "    @ConjureServerEndpoint",
                        "    @Deprecated(forRemoval = true)",
                        "    void deprecatedForRemovalMethod();",
                        "    @Deprecated",
                        "    void deprecatedMethod();",
                        "    void normalMethod();",
                        "  }",
                        "  static class Impl implements Iface {",
                        "    @Override",
                        "    public void deprecatedForRemovalMethod() {",
                        "      return;",
                        "    }",
                        "    @Override",
                        "    public void deprecatedMethod() {",
                        "      return;",
                        "    }",
                        "    @Override",
                        "    public void normalMethod() {",
                        "      return;",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.conjure.RemoveLater;",
                        "import com.palantir.conjure.java.lib.internal.ConjureServerEndpoint;",
                        "class Test {",
                        "  interface Iface {",
                        "    @ConjureServerEndpoint",
                        "    @Deprecated(forRemoval = true)",
                        "    void deprecatedForRemovalMethod();",
                        "    @Deprecated",
                        "    void deprecatedMethod();",
                        "    void normalMethod();",
                        "  }",
                        "  static class Impl implements Iface {",
                        "    @RemoveLater",
                        "    public void deprecatedForRemovalMethod() {",
                        "      return;",
                        "    }",
                        "    @Override",
                        "    public void deprecatedMethod() {",
                        "      return;",
                        "    }",
                        "    @Override",
                        "    public void normalMethod() {",
                        "      return;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix(String... args) {
        return RefactoringValidator.of(DeprecatedEndpointImplRemoval.class, getClass(), args);
    }
}
