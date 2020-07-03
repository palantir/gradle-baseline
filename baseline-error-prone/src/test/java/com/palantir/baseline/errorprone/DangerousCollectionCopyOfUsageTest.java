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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DangerousCollectionCopyOfUsageTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousCollectionCopyOfUsage.class, getClass());
    }

    @Test
    public void fails_on_set_copy_Of() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Set;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       // BUG: Diagnostic contains: Should not use java.util.{Map,Set}.copyOf methods",
                        "       Set.copyOf(Set.of());",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_on_map_copy_Of() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Map;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       // BUG: Diagnostic contains: Should not use java.util.{Map,Set}.copyOf methods",
                        "       Map.copyOf(Map.of());",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void allows_guava_copy_of() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.ImmutableMap;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       // this is fine, even though ImmutableMap extends Map",
                        "       ImmutableMap.copyOf(ImmutableMap.of());",
                        "   }",
                        "}")
                .doTest();
    }
}
