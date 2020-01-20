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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class ReverseDnsLookupTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import java.net.InetAddress;",
                        "import java.net.InetSocketAddress;",
                        "class Test {",
                        "  void f(InetAddress ia, InetSocketAddress isa) {",
                        "    ia.getHostName();",
                        "    ia.getCanonicalHostName();",
                        "    isa.getHostName();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.net.InetAddress;",
                        "import java.net.InetSocketAddress;",
                        "class Test {",
                        "  void f(InetAddress ia, InetSocketAddress isa) {",
                        "    ia.getHostAddress();",
                        "    ia.getHostAddress();",
                        "    isa.getHostString();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ReverseDnsLookup(), getClass());
    }
}
