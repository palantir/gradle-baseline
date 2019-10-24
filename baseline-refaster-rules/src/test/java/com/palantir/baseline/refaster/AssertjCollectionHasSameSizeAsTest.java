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

public class AssertjCollectionHasSameSizeAsTest {

    @Test
    public void test() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjCollectionHasSameSizeAs.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "import java.util.Collection;",
                        "public class Test {",
                        "  void f(List<String> a, Collection<String> b, Iterable<String> c, List<String> target) {",
                        "    assertThat(a).hasSize(target.size());",
                        "    assertThat(b).hasSize(target.size());",
                        "    assertThat(c).hasSize(target.size());",
                        "    assertThat(a).describedAs(\"desc\").hasSize(target.size());",
                        "    assertThat(b).describedAs(\"desc\").hasSize(target.size());",
                        "    assertThat(c).describedAs(\"desc\").hasSize(target.size());",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "import java.util.Collection;",
                        "public class Test {",
                        "  void f(List<String> a, Collection<String> b, Iterable<String> c, List<String> target) {",
                        "    assertThat(a).hasSameSizeAs(target);",
                        "    assertThat(b).hasSameSizeAs(target);",
                        "    assertThat(c).hasSameSizeAs(target);",
                        "    assertThat(a).describedAs(\"desc\").hasSameSizeAs(target);",
                        "    assertThat(b).describedAs(\"desc\").hasSameSizeAs(target);",
                        "    assertThat(c).describedAs(\"desc\").hasSameSizeAs(target);",
                        "  }",
                        "}");
    }

    @Test
    public void testArray() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjCollectionHasSameSizeAsArray.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "import java.util.Collection;",
                        "public class Test {",
                        "  void f(List<String> a, Collection<String> b, Iterable<String> c, String[] target) {",
                        "    assertThat(a).hasSize(target.length);",
                        "    assertThat(b).hasSize(target.length);",
                        "    assertThat(c).hasSize(target.length);",
                        "    assertThat(a).describedAs(\"desc\").hasSize(target.length);",
                        "    assertThat(b).describedAs(\"desc\").hasSize(target.length);",
                        "    assertThat(c).describedAs(\"desc\").hasSize(target.length);",
                        "    assertThat(c).describedAs(\"foo %s\", \"bar\").hasSize(target.length);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "import java.util.Collection;",
                        "public class Test {",
                        "  void f(List<String> a, Collection<String> b, Iterable<String> c, String[] target) {",
                        "    assertThat(a).hasSameSizeAs(target);",
                        "    assertThat(b).hasSameSizeAs(target);",
                        "    assertThat(c).hasSameSizeAs(target);",
                        "    assertThat(a).describedAs(\"desc\").hasSameSizeAs(target);",
                        "    assertThat(b).describedAs(\"desc\").hasSameSizeAs(target);",
                        "    assertThat(c).describedAs(\"desc\").hasSameSizeAs(target);",
                        "    assertThat(c).describedAs(\"foo %s\", \"bar\").hasSameSizeAs(target);",
                        "  }",
                        "}");
    }
}
