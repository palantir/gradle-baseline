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

class ObjectsHashCodeUnnecessaryVarargsTest {

    @Test
    public void test() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object a, Object[] b, String[] c, String d) {",
                        "    Objects.hash(a);",
                        "    Objects.hash(a, a);",
                        "    Objects.hash(b);",
                        "    Objects.hash(c);",
                        "    Objects.hash(d);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object a, Object[] b, String[] c, String d) {",
                        "    Objects.hashCode(a);",
                        "    Objects.hash(a, a);",
                        "    Objects.hash(b);",
                        "    // BUG: Diagnostic contains: non-varargs call of varargs method",
                        "    Objects.hash(c);",
                        "    Objects.hashCode(d);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ObjectsHashCodeUnnecessaryVarargs(), getClass());
    }

    public CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ObjectsHashCodeUnnecessaryVarargs.class, getClass());
    }
}
