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

public final class PatternAsKeyOfSetOrMapTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PatternAsKeyOfSetOrMap.class, getClass());
    }

    @Test
    public void testInvalidMapKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: Pattern does not override equals",
                        "        return new HashMap<Pattern, String>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidSetKey() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        // BUG: Diagnostic contains: Pattern does not override equals",
                        "        return new HashSet<Pattern>();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void testValidMap() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.regex.Pattern;",
                        "class Test {",
                        "    private Object test() {",
                        "        return new HashMap<String, Pattern>();",
                        "    }",
                        "}")
                .doTest();
    }
}
