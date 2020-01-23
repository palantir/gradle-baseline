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

public class Utf8LengthTest {

    @Test
    public void test() {
        RefasterTestHelper.forRefactoring(Utf8Length.class)
                .withInputLines(
                        "Test",
                        "import java.nio.charset.StandardCharsets;",
                        "public class Test {",
                        "  int i = \"hello world\".getBytes(StandardCharsets.UTF_8).length;",
                        "}")
                .hasOutputLines(
                        "import com.google.common.base.Utf8;",
                        "import java.nio.charset.StandardCharsets;",
                        "public class Test {",
                        "  int i = Utf8.encodedLength(\"hello world\");",
                        "}");
    }
}
