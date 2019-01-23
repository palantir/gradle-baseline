/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public final class PreferSafeLoggingPreconditionsTests {
    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PreferSafeLoggingPreconditions.class, getClass());
    }

    @Test
    public void testObjectsRequireNonNull() {
        failObjects("Objects.requireNonNull(param);");
    }

    @Test
    public void testObjectsRequireNonNullConstantString() {
        failObjects("Objects.requireNonNull(param, \"constant\");");
    }

    @Test
    public void testObjectsRequireNonNullNonConstantString() {
        passObjects("Objects.requireNonNull(param, String.format(\"constant %s\", param));");
    }

    @Test
    public void testObjectsRequireNonNullStringSupplier() {
        passObjects("Objects.requireNonNull(param, () -> \"test\");");
    }

    @Test
    public void testPreconditionsCheckArgumentNoParams() {
        failGuava("Preconditions.checkArgument(param != \"string\");");
    }

    @Test
    public void testPreconditionsCheckArgumentConstantStringNoParams() {
        failGuava("Preconditions.checkArgument(param != \"string\", \"constant\");");
    }

    @Test
    public void testPreconditionsCheckArgumentNonConstantStringNoParams() {
        passGuava("Preconditions.checkArgument(param != \"string\", String.format(\"constant %s\", param));");
    }

    @Test
    public void testPreconditionsCheckArgumentParams() {
        passGuava("Preconditions.checkArgument(param != \"string\", \"message %s\", \"a\");");
    }

    @Test
    public void testPreconditionsCheckStateNoParams() {
        failGuava("Preconditions.checkState(param != \"string\");");
    }

    @Test
    public void testPreconditionsCheckStateConstantStringNoParams() {
        failGuava("Preconditions.checkState(param != \"string\", \"constant\");");
    }

    @Test
    public void testPreconditionsCheckStateNonConstantStringNoParams() {
        passGuava("Preconditions.checkState(param != \"string\", String.format(\"constant %s\", param));");
    }

    @Test
    public void testPreconditionsCheckStateParams() {
        passGuava("Preconditions.checkState(param != \"string\", \"message %s\", \"a\");");
    }

    @Test
    public void testPreconditionsCheckNotNullNoParams() {
        // CHECKSTYLE:OFF
        failGuava("Preconditions.checkNotNull(param);");
        // CHECKSTYLE:ON
    }

    @Test
    public void testPreconditionsCheckNotNullConstantStringNoParams() {
        failGuava("Preconditions.checkNotNull(param, \"constant\");");
    }

    @Test
    public void testPreconditionsCheckNotNullNonConstantStringNoParams() {
        passGuava("Preconditions.checkNotNull(param, String.format(\"constant %s\", param));");
    }

    @Test
    public void testPreconditionsCheckNotNullParams() {
        passGuava("Preconditions.checkNotNull(param, \"message %s\", \"a\");");
    }

    @Test
    public void testPreconditionsAutoFixShortNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    com.palantir.logsafe.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testPreconditionsAutoFixFullNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "class Test {",
                "  void f(String param) {",
                "    com.google.common.base.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedGuavaPreconditionsFullNamesAndLogSafeShortNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    com.google.common.base.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkNotNull(param, \"constant\");",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedGuavaPreconditionsShortNamesAndLogSafeFullNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    com.palantir.logsafe.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testObjectsAutoFixShortNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    Objects.requireNonNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testObjectsAutoFixFullNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "class Test {",
                "  void f(String param) {",
                "    java.util.Objects.requireNonNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedObjectsFullNamesAndLogSafeShortNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    java.util.Objects.requireNonNull(param, \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedObjectsShortNamesAndLogSafeFullNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    Objects.requireNonNull(param, \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedGuavaPreconditionsAndObjectsAutoFixShortNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    Objects.requireNonNull(param, \"constant\");",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.palantir.logsafe.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testMixedGuavaPreconditionsAndObjectsAutoFixFullNames() {
        BugCheckerRefactoringTestHelper.newInstance(new PreferSafeLoggingPreconditions(), getClass()).addInputLines(
                "Test.java",
                "class Test {",
                "  void f(String param) {",
                "    java.util.Objects.requireNonNull(param, \"constant\");",
                "    com.google.common.base.Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkState(param != \"string\", \"constant\");",
                "    com.google.common.base.Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").addOutputLines(
                "Test.java",
                "import com.palantir.logsafe.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "    Preconditions.checkArgument(param != \"string\", \"constant\");",
                "    Preconditions.checkState(param != \"string\", \"constant\");",
                "    Preconditions.checkNotNull(param, \"constant\");",
                "  }",
                "}").doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private void passObjects(String precondition) {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    " + precondition,
                "  }",
                "}")
                .doTest();
    }

    private void passGuava(String precondition) {
        compilationHelper.addSourceLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    " + precondition,
                "  }",
                "}")
                .doTest();
    }

    private void failObjects(String precondition) {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.Objects;",
                "class Test {",
                "  void f(String param) {",
                "    // BUG: Diagnostic contains: call can be replaced",
                "    " + precondition,
                "  }",
                "}")
                .doTest();
    }

    private void failGuava(String precondition) {
        compilationHelper.addSourceLines(
                "Test.java",
                "import com.google.common.base.Preconditions;",
                "class Test {",
                "  void f(String param) {",
                "    // BUG: Diagnostic contains: call can be replaced",
                "    " + precondition,
                "  }",
                "}")
                .doTest();
    }
}
