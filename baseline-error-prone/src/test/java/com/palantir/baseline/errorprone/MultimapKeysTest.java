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
import org.junit.Before;
import org.junit.Test;

public class MultimapKeysTest {

    private static final String errorMsg = "BUG: Diagnostic contains: "
            + "Multimap.keys() is usually an error. Did you mean to use Multimap.keySet()?";

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(MultimapKeys.class, getClass());
    }

    @Test
    public void testUsesMultimapKeys_fails() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import com.google.common.collect.Multimap;",
                "class Test {",
                "  void f(Multimap<Integer, Integer> multimap) {",
                "// " + errorMsg,
                "    for (int i : multimap.keys()) {",
                "        continue;",
                "    }",
                "  }",
                "}"
        ).doTest();
    }

    @Test
    public void testUsesMultimapKeysInSubclass_fails() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import com.google.common.collect.ImmutableMultimap;",
                "class Test {",
                "  void f(ImmutableMultimap<Integer, Integer> multimap) {",
                "// " + errorMsg,
                "    for (int i : multimap.keys()) {",
                "        continue;",
                "    }",
                "  }",
                "}"
        ).doTest();
    }

    @Test
    public void testUsesMultimapKeySet_passes() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import com.google.common.collect.Multimap;",
                "class Test {",
                "  void f(Multimap<Integer, Integer> multimap) {",
                "// ",
                "    for (int i : multimap.keySet()) {",
                "        continue;",
                "    }",
                "  }",
                "}"
        ).doTest();
    }
}
