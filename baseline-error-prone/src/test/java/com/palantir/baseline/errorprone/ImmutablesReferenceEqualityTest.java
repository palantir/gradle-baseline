/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

public class ImmutablesReferenceEqualityTest {

    @Test
    public void test() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.immutables.value.Value;",
                        "class Test {",
                        "  static boolean f(Foo foo1, Foo foo2) {",
                        "    // BUG: Diagnostic contains: foo1.equals(foo2)",
                        "    return foo1 == foo2;",
                        "  }",
                        "  @Value.Immutable",
                        "  interface Foo {}",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesReferenceEquality.class, getClass());
    }
}
