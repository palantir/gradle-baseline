/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

class DnsLookupTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import java.net.InetSocketAddress;",
                        "class Test {",
                        "  InetSocketAddress f() {",
                        "    return new InetSocketAddress(\"host\", 443);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.net.InetSocketAddress;",
                        "class Test {",
                        "  InetSocketAddress f() {",
                        "    return InetSocketAddress.createUnresolved(\"host\", 443);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(DnsLookup.class, getClass());
    }
}
