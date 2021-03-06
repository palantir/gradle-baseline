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

public class PreferImmutableStreamExCollectionsTest {

    @Test
    void toMap() {
        fix().addInputLines(
                        "Test.java",
                        "import one.util.streamex.EntryStream;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> map = EntryStream.of(Map.of(\"hello\", \"world\")).toMap();",
                        "  EntryStream<String, String> entryStream = EntryStream.of(Map.of(\"hello\", \"world\"));",
                        "  Map<String, String> entryStream2 = entryStream.toMap();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import one.util.streamex.EntryStream;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  Map<String, String> map = EntryStream.of(Map.of(\"hello\", \"world\")).toImmutableMap();",
                        "  EntryStream<String, String> entryStream = EntryStream.of(Map.of(\"hello\", \"world\"));",
                        "  Map<String, String> entryStream2 = entryStream.toImmutableMap();",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void toSet() {
        fix().addInputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.stream.Collectors;",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> s = StreamEx.of(\"Hello\").toSet();",
                        "  Set<String> s2 = StreamEx.of(\"Hello\").collect(Collectors.toSet());",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.stream.Collectors;",
                        "import java.util.Set;",
                        "public class Test {",
                        "  Set<String> s = StreamEx.of(\"Hello\").toImmutableSet();",
                        "  Set<String> s2 = StreamEx.of(\"Hello\").toImmutableSet();",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void toList() {
        fix().addInputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.List;",
                        "import static java.util.stream.Collectors.toList;",
                        "public class Test {",
                        "  List<String> s = StreamEx.of(\"Hello\").toList();",
                        "  List<String> s2 = StreamEx.of(\"Hello\").collect(toList());",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import one.util.streamex.StreamEx;",
                        "import java.util.List;",
                        "import static java.util.stream.Collectors.toList;",
                        "public class Test {",
                        "  List<String> s = StreamEx.of(\"Hello\").toImmutableList();",
                        "  List<String> s2 = StreamEx.of(\"Hello\").toImmutableList();",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(PreferImmutableStreamExCollections.class, getClass());
    }
}
