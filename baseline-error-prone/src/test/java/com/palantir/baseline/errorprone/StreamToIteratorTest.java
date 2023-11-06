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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public class StreamToIteratorTest {
    @Test
    void testFix() {
        CompilationTestHelper.newInstance(StreamToIterator.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "private Object test() {",
                        "// BUG: Diagnostic contains: StreamToIterator",
                        "  return Stream.of(1, 2).iterator();",
                        "}",
                        "}")
                .doTest();
    }
}
