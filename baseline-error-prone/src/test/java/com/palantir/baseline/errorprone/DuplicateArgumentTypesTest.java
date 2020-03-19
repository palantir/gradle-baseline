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

public final class DuplicateArgumentTypesTest {

    @Test
    void testType() {
        fix().addSourceLines(
                "Test.java",
                "import com.google.common.annotations.VisibleForTesting;",
                "import java.util.function.Supplier;",
                "public class Test {",
                "  public void myah3(Supplier<Number> a, Supplier<String> b) {}",
                // "  public void myah0(Number a, Integer b) {}",
                "  public void myah(byte a, Number b) {}",
                "  public void myah2(int a, String b) {}",
                "}")
                .doTest();
    }

    // private RefactoringValidator fix() {
    //     return RefactoringValidator.of(new DuplicateArgumentTypes(), getClass());
    // }
    private CompilationTestHelper fix() {
        return CompilationTestHelper.newInstance(DuplicateArgumentTypes.class, getClass());
    }
}
