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

package com.palantir.baseline.refaster;

import org.junit.Test;

public class SortedFirstNaturalTest {

    @Test
    public void test() {
        RefasterTestHelper.forRefactoring(SortedFirstNatural.class)
                .withInputLines(
                        "Test",
                        "import java.util.*;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream()",
                        "      .sorted()",
                        "      .findFirst();",
                        "}")
                .hasOutputLines(
                        "import java.util.*;",
                        "import java.util.Comparator;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream()"
                                + ".min(Comparator.naturalOrder());",
                        "}");
    }
}
