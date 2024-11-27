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

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

public class InvocationTargetExceptionGetTargetExceptionTest {
    @Test
    public void test_basic() {
        fix().addInputLines(
                        "Test.java",
                        "import " + InvocationTargetException.class.getName() + ";",
                        "class Test {",
                        "  static Throwable f(InvocationTargetException foo) {",
                        "    return foo.getTargetException();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + InvocationTargetException.class.getName() + ";",
                        "class Test {",
                        "  static Throwable f(InvocationTargetException foo) {",
                        "    return foo.getCause();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_subclass() {
        fix().addInputLines(
                        "Test.java",
                        "import " + InvocationTargetException.class.getName() + ";",
                        "class Test {",
                        "  class TestException extends InvocationTargetException {}",
                        "  public Throwable getCause(TestException foo) {",
                        "    return foo.getTargetException();", // This should change
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + InvocationTargetException.class.getName() + ";",
                        "class Test {",
                        "  class TestException extends InvocationTargetException {}",
                        "  public Throwable getCause(TestException foo) {",
                        "    return foo.getCause();",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(InvocationTargetExceptionGetTargetException.class, getClass());
    }
}
