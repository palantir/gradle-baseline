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

import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.Test;

public class AddAllArrayToBuilderTest {

    @Test
    public void testAddAllArray() {
        // Refaster checks ExpressionPatterns against the JCExpression AST node, this isn't use the same after Java 8
        assumeThat(System.getProperty("java.specification.version")).isEqualTo("1.8");

        RefasterTestHelper.forRefactoring(AddAllArrayToBuilder.class)
                .withInputLines(
                        "Test",
                        "import com.google.common.collect.ImmutableList;",
                        "import java.util.*;",
                        "public class Test {",
                        "  private void test(String... args) {",
                        "    List<String> list = ImmutableList.<String>builder().addAll(Arrays.asList(args)).build();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import com.google.common.collect.ImmutableList;",
                        "import java.util.*;",
                        "public class Test {",
                        "  private void test(String... args) {",
                        "    List<String> list = ImmutableList.<String>builder().add(args).build();",
                        "  }",
                        "}");
    }
}
