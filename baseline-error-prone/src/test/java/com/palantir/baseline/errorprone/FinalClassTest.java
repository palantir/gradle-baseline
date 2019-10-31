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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class FinalClassTest {

    @Test
    void testSimple() {
        helper().addSourceLines(
                "Test.java",
                "// BUG: Diagnostic contains: should be declared final",
                "public class Test {",
                "  private Test() {}",
                "}"
        ).doTest();
    }

    @Test
    void testSimple_fix() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  private Test() {}",
                        "}"
                )
                .addOutputLines(
                        "Test.java",
                        "public final class Test {",
                        "  private Test() {}",
                        "}"
                ).doTest();
    }

    @Test
    void testNested() {
        helper().addSourceLines(
                "Test.java",
                "public final class Test {",
                "  private Test() {}",
                "  // BUG: Diagnostic contains: should be declared final",
                "  public static class Nested {",
                "    private Nested() {}",
                "  }",
                "}"
        ).doTest();
    }

    @Test
    void testNested_fix() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public final class Test {",
                        "  private Test() {}",
                        "  public static class Nested {",
                        "    private Nested() {}",
                        "  }",
                        "}"
                )
                .addOutputLines(
                        "Test.java",
                        "public final class Test {",
                        "  private Test() {}",
                        "  public static final class Nested {",
                        "    private Nested() {}",
                        "  }",
                        "}"
                ).doTest();
    }

    @Test
    void testNestedInInterface() {
        helper().addSourceLines(
                "Test.java",
                "public interface Test {",
                "  // BUG: Diagnostic contains: should be declared final",
                "  class Nested {",
                "    private Nested() {}",
                "  }",
                "}"
        ).doTest();
    }

    @Test
    void testNestedInInterface_fix() {
        fix()
                .addInputLines(
                        "Test.java",
                        "public interface Test {",
                        "  class Nested {",
                        "    private Nested() {}",
                        "  }",
                        "}"
                )
                .addOutputLines(
                        "Test.java",
                        "public interface Test {",
                        "  final class Nested {",
                        "    private Nested() {}",
                        "  }",
                        "}"
                ).doTest();
    }

    @Test
    void testDoesNotMatchGeneratedConstructor() {
        helper().addSourceLines(
                "Test.java",
                "public class Test {",
                "}"
        ).doTest();
    }

    @Test
    void testCheckstyleSuppression_lowerCase() {
        helper().addSourceLines(
                "Test.java",
                "@SuppressWarnings(\"checkstyle:finalclass\")",
                "public class Test {",
                "  private Test() {}",
                "}"
        ).doTest();
    }

    @Test
    void testCheckstyleSuppression_camelCase() {
        helper().addSourceLines(
                "Test.java",
                "@SuppressWarnings(\"checkstyle:FinalClass\")",
                "public class Test {",
                "  private Test() {}",
                "}"
        ).doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(FinalClass.class, getClass());
    }

    private BugCheckerRefactoringTestHelper fix() {
        return BugCheckerRefactoringTestHelper.newInstance(new FinalClass(), getClass());
    }
}
