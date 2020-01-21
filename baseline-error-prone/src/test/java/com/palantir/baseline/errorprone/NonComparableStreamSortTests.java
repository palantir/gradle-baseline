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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class NonComparableStreamSortTests {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(NonComparableStreamSort.class, getClass());
    }

    @Test
    public void should_fail_on_non_comparable_stream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.ArrayList;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<Object> list = new ArrayList<>();",
                        "       // BUG: Diagnostic contains: Stream.sorted() should only be called on streams of"
                                + " Comparable",
                        "       list.stream().sorted();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_on_comparable_stream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.ArrayList;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<String> list = new ArrayList<>();",
                        "       list.stream().sorted();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_fail_without_direct_types() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.ArrayList;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<Iface> list = new ArrayList<>();",
                        "       // BUG: Diagnostic contains: Stream.sorted() should only be called on streams of"
                                + " Comparable",
                        "       list.stream().sorted();",
                        "   }",
                        "   interface Iface {}",
                        "}")
                .doTest();
    }
}
