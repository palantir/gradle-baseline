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

public final class DangerousRecordArrayFieldTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousRecordArrayField.class, getClass());
    }

    @Test
    public void testSimpleRecord() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private record MyRecord(String name, List<Integer> payload) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testArray() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Record type has an array field and",
                        "    private record MyRecord(String name, byte[] payload) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testArray_withEqualsNoHashcode() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Record type has an array field and",
                        "    private record MyRecordE(String name, byte[] payload) {",
                        "        public boolean equals(Object other) { return false; }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testArray_noEqualsWithHashcode() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    // BUG: Diagnostic contains: Record type has an array field and",
                        "    private record MyRecordH(String name, byte[] payload) {",
                        "        public int hashCode() { return 0; }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testArray_withEqualsWithHashcode() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private record MyRecordEH(String name, byte[] payload) {",
                        "        public boolean equals(Object other) { return false; }",
                        "        public int hashCode() { return 0; }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testArrayInMethod() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private void myMethod() {",
                        "       // BUG: Diagnostic contains: Record type has an array field and",
                        "       record MyRecord(String name, byte[] payload) {}",
                        "    }",
                        "}")
                .doTest();
    }
}
