/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

public class StreamExPreferImmutableOutputTest {

    @Test
    void toMap() {
        fix().addInputLines(
                        "Test.java",
                        "import one.util.streamex.EntryStream;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> entryStream = EntryStream.of(Map.of(\"hello\", \"world\")).toMap();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import one.util.streamex.EntryStream;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> entryStream = EntryStream.of(Map.of(\"hello\", \"world\"))"
                                + ".toImmutableMap();",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void toSet() {
        fix().addInputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> s = StreamEx.of(\"Hello\").toSet();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> s = StreamEx.of(\"Hello\").toImmutableSet();",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new StreamExPreferImmutableOutput(), getClass());
    }
}
