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

package com.palantir.baseline.refaster;

import org.junit.Test;

public class CollectionCopyOfTest {

    @Test
    public void testSet() {
        RefasterTestHelper.forRefactoring(SetCopyOf.class)
                .withInputLines(
                        "Test",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> set = Set.copyOf(Set.of());",
                        "}")
                .hasOutputLines(
                        "import com.google.common.collect.ImmutableSet;",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> set = ImmutableSet.copyOf(Set.of());",
                        "}");
    }

    @Test
    public void testMap() {
        RefasterTestHelper.forRefactoring(MapCopyOf.class)
                .withInputLines(
                        "Test",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> map = Map.copyOf(Map.of());",
                        "}")
                .hasOutputLines(
                        "import com.google.common.collect.ImmutableMap;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> map = ImmutableMap.copyOf(Map.of());",
                        "}");
    }
}
