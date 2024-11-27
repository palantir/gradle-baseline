/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class SortedStreamFirstElementTest {

    @Test
    public void test_basic() {
        fix().addInputLines(
                        "TestBasic.java",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestBasic {",
                        "  public Optional<Integer> basic(Stream<Integer> s) {",
                        "    return s.sorted().findFirst();",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestBasic.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestBasic {",
                        "  public Optional<Integer> basic(Stream<Integer> s) {",
                        "    return s.min(Comparator.naturalOrder());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_comparator_already_imported() {
        fix().addInputLines(
                        "TestComparatorAlreadyImported.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestComparatorAlreadyImported {",
                        "  public Optional<Integer> f(Stream<Integer> s) {",
                        "    return s.sorted().findFirst();",
                        "  }",
                        "  public Optional<Integer> g(Stream<Integer> s) {",
                        "    return s.min(Comparator.naturalOrder());",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestComparatorAlreadyImported.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestComparatorAlreadyImported {",
                        "  public Optional<Integer> f(Stream<Integer> s) {",
                        "    return s.min(Comparator.naturalOrder());",
                        "  }",
                        "  public Optional<Integer> g(Stream<Integer> s) {",
                        "    return s.min(Comparator.naturalOrder());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_templated() {
        fix().addInputLines(
                        "TestBasic.java",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestBasic<T extends Comparable<? super T>> {",
                        "  public Optional<T> basic(Stream<T> s) {",
                        "    return s.sorted().findFirst();",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestBasic.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestBasic<T extends Comparable<? super T>> {",
                        "  public Optional<T> basic(Stream<T> s) {",
                        "    return s.min(Comparator.naturalOrder());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_comparator() {
        fix().addInputLines(
                        "TestComparator.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestComparator {",
                        "  public Optional<Integer> f(Stream<Integer> s, Comparator c) {",
                        "    return s.sorted(c).findFirst();",
                        "  }",
                        "}")
                .addOutputLines(
                        "TestComparator.java",
                        "import " + Comparator.class.getCanonicalName() + ";",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "import " + Stream.class.getCanonicalName() + ";",
                        "class TestComparator {",
                        "  public Optional<Integer> f(Stream<Integer> s, Comparator c) {",
                        "    return s.min(c);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(SortedStreamFirstElement.class, getClass());
    }
}
