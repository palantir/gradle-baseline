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
import org.junit.jupiter.api.Test;

public final class AvoidNewHashMapIntTest {

    @Test
    public void testRewriteHashSetConstructor() {
        RefactoringValidator.of(AvoidNewHashMapInt.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.util.HashSet;",
                        "import java.util.Set;",
                        "class Test {{ Set<Integer> set = new HashSet<>(10); }}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Sets;",
                        "import java.util.HashSet;", // HACK
                        "import java.util.Set;",
                        "class Test {{ Set<Integer> set = Sets.newHashSetWithExpectedSize(10); }}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testRewriteHashMapConstructor() {
        RefactoringValidator.of(AvoidNewHashMapInt.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import java.util.HashMap;",
                        "import java.util.Map;",
                        "class Test {{ Map<Integer, Integer> map = new HashMap<>(10); }}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.collect.Maps;",
                        "import java.util.HashMap;", // HACK
                        "import java.util.Map;",
                        "class Test {{ Map<Integer, Integer> map = Maps.newHashMapWithExpectedSize(10); }}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
