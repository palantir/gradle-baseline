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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public final class LogsafeRidTests {

    @Test
    public void ignores_ridarg_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.Arg;",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.UUID;",
                        "class RidArg {",
                        "  @SuppressWarnings(\"LogsafeRid\")",
                        "  public static Arg<ResourceIdentifier> of(ResourceIdentifier rid) {",
                        "    return SafeArg.of(\"rid\", rid);",
                        "  }",
                        "}",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    RidArg.of(rid);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_safe_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    // BUG: Diagnostic contains: Arguments with with rid values are not guaranteed to be safe.",
                        "    SafeArg.of(\"rid\", rid);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_safe_rid_arg() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    SafeArg.of(\"rid\", rid);",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    UnsafeArg.of(\"rid\", rid);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_non_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"v\", \"value\");",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_safe_optional_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Optional;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    // BUG: Diagnostic contains: Arguments with with rid values are not guaranteed to be safe.",
                        "    SafeArg.of(\"rid\", Optional.of(rid));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_safe_optional_rid_arg() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Optional;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    SafeArg.of(\"rid\", Optional.of(rid));",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Optional;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    UnsafeArg.of(\"rid\", Optional.of(rid));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_optional_non_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import java.util.Optional;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"v\", Optional.of(1));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_safe_map_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Map;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    // BUG: Diagnostic contains: Arguments with with rid values are not guaranteed to be safe.",
                        "    SafeArg.of(\"rid\", Map.of(rid, 1));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_safe_map_rid_arg() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Map;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    SafeArg.of(\"rid\", Map.of(1, rid));",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Map;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    UnsafeArg.of(\"rid\", Map.of(1, rid));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_map_non_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import java.util.Map;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"v\", Map.of(1, \"1\"));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_safe_collection_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    // BUG: Diagnostic contains: Arguments with with rid values are not guaranteed to be safe.",
                        "    SafeArg.of(\"rid\", Set.of(rid));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_safe_collection_rid_arg() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    SafeArg.of(\"rid\", Set.of(rid));",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    UnsafeArg.of(\"rid\", Set.of(rid));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_collection_non_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import java.util.Set;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"v\", Set.of(1));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_safe_nested_collections_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    // BUG: Diagnostic contains: Arguments with with rid values are not guaranteed to be safe.",
                        "    SafeArg.of(\"rid\", Set.of(List.of(rid)));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_safe_nested_collections_rid_arg() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    SafeArg.of(\"rid\", Set.of(List.of(rid)));",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.ri.ResourceIdentifier;",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "import java.util.UUID;",
                        "class Test {",
                        "  void f() {",
                        "    ResourceIdentifier rid = ResourceIdentifier.of(\"service\", \"instance\", \"locator\", "
                                + "UUID.randomUUID().toString());\n",
                        "    UnsafeArg.of(\"rid\", Set.of(List.of(rid)));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_nested_collections_non_rid_arg() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"v\", Set.of(List.of(1)));",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    private static RefactoringValidator getRefactoringHelper() {
        return RefactoringValidator.of(LogsafeRid.class, LogsafeRid.class);
    }

    private static CompilationTestHelper getCompilationHelper() {
        return CompilationTestHelper.newInstance(LogsafeRid.class, LogsafeRid.class);
    }
}
