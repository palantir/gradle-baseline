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

public class MockitoVerifyZeroInteractionsTest {

    @Test
    public void testSingleArg() {
        RefasterTestHelper.forRefactoring(MockitoVerifyZeroInteractions.class)
                .withInputLines(
                        "Test",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object mock) {",
                        "    verifyZeroInteractions(mock);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.mockito.Mockito.verifyNoMoreInteractions;",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object mock) {",
                        "    verifyNoMoreInteractions(mock);",
                        "  }",
                        "}");
    }

    @Test
    public void testMultipleArgs() {
        RefasterTestHelper.forRefactoring(MockitoVerifyZeroInteractions.class)
                .withInputLines(
                        "Test",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object mock1, Object mock2) {",
                        "    verifyZeroInteractions(mock1, mock2);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.mockito.Mockito.verifyNoMoreInteractions;",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object mock1, Object mock2) {",
                        "    verifyNoMoreInteractions(mock1, mock2);",
                        "  }",
                        "}");
    }

    @Test
    public void testVarArgs() {
        RefasterTestHelper.forRefactoring(MockitoVerifyZeroInteractions.class)
                .withInputLines(
                        "Test",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object... mocks) {",
                        "    verifyZeroInteractions(mocks);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.mockito.Mockito.verifyNoMoreInteractions;",
                        "import static org.mockito.Mockito.verifyZeroInteractions;",
                        "public class Test {",
                        "  void test(Object... mocks) {",
                        "    verifyNoMoreInteractions(mocks);",
                        "  }",
                        "}");
    }
}
