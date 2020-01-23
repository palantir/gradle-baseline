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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class LambdaMethodReferenceTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(LambdaMethodReference.class, getClass());
    }

    @Test
    public void testMethodReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPositive_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_block() {
        RefactoringValidator.of(new LambdaMethodReference(), getClass())
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(); });",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_block_localMethod() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        // A future improvement may rewrite the following to 'orElseGet(this::bar)'
                        "    return optional.orElseGet(() -> { return bar(); });",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_block_localMethod() {
        RefactoringValidator.of(new LambdaMethodReference(), getClass())
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return bar(); });",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        // This is not modified, may be improved later
                        "    return optional.orElseGet(() -> { return bar(); });",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(1); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPositive_expression() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> ImmutableList.of());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_expression() {
        RefactoringValidator.of(new LambdaMethodReference(), getClass())
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> ImmutableList.of());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_expression_localMethod() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        // A future improvement may rewrite the following to 'orElseGet(this::bar)'
                        "    return optional.orElseGet(() -> bar());",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_expression_localMethod() {
        RefactoringValidator.of(new LambdaMethodReference(), getClass())
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> bar());",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        // This is not modified, may be improved later
                        "    return optional.orElseGet(() -> bar());",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_expression() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> ImmutableList.of(1));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNegative_expression_chain() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Object foo(Optional<Object> optional) {",
                        // It's important that this is not rewritten to 'optional.orElseGet(ImmutableList.of(1)::size)'
                        // which create a new list eagerly, and returns a supplier for the new instances 'size()'
                        // function.
                        "    return optional.orElseGet(() -> ImmutableList.of(1).size());",
                        "  }",
                        "}")
                .doTest();
    }
}
