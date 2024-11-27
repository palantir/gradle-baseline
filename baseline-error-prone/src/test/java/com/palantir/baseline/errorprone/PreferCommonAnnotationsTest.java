/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

public final class PreferCommonAnnotationsTest {
    @Test
    public void desired_import_remains_unchanged() {
        fix().addInputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public final class Client {",
                        "  @VisibleForTesting",
                        "  public int getValue() {",
                        "    return 42;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void other_import_is_replaced() {
        fix().addInputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import org.jetbrains.annotations.VisibleForTesting;",
                        "public final class Client {",
                        "  @VisibleForTesting",
                        "  public int getValue() {",
                        "    return 42;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import com.google.common.annotations.VisibleForTesting;",
                        "public final class Client {",
                        "  @VisibleForTesting",
                        "  public int getValue() {",
                        "    return 42;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fully_qualified_annotations_are_not_currently_rewritten() {
        CompilationTestHelper.newInstance(PreferCommonAnnotations.class, getClass())
                .addSourceLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "public final class Client {",
                        // NOTE: fully-qualified annotations are not currently re-written
                        "  @org.jetbrains.annotations.VisibleForTesting",
                        "  public int getValue() {",
                        "    return 42;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void wildcard_import_annotations_are_not_currently_rewritten() {
        CompilationTestHelper.newInstance(PreferCommonAnnotations.class, getClass())
                .addSourceLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import org.jetbrains.annotations.*;",
                        "public final class Client {",
                        // NOTE: wildcard-imported annotations are not currently re-written
                        "  @VisibleForTesting",
                        "  public int getValue() {",
                        "    return 42;",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(PreferCommonAnnotations.class, getClass());
    }
}
