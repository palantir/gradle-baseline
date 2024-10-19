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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DangerousGuavaTransformValuesUsageUsageTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousGuavaTransformValuesUsage.class, getClass());
    }

    @Test
    public void should_error_when_transforms_values_is_used() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Map;",
                        "import com.google.common.collect.Maps;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       Map<Integer, Integer> map = Map.of(1, 2, 3, 4, 5, 6);",
                        "       Maps.transformValues(map, value -> value + 1);",
                        "   }",
                        "}")
                .doTest();
    }
}
