/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

public class PreferTypeSafeStubbingTest {

    @Test
    public void testCorrectMock() {
        fix().addInputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.mock;",
                        "import static org.mockito.Mockito.when;",
                        "class Test {",
                        "  void f() {",
                        "    Object obj = mock(Object.class);",
                        "    when(obj.equals(any())).thenReturn(true);",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void testMockWithArgMatcher() {
        fix().addInputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "class Test {",
                        "  void f() {",
                        "    Object obj = mock(Object.class);",
                        "    doReturn(true).when(obj).equals(any());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "import static org.mockito.Mockito.when;",
                        "class Test {",
                        "  void f() {",
                        "    Object obj = mock(Object.class);",
                        "    when(obj.equals(any())).thenReturn(true);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMockWithArgMatcherMultipleStubbing() {
        fix().addInputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "class Test {",
                        "  void f() {",
                        "    Object obj = mock(Object.class);",
                        "    doReturn(true).doReturn(false).when(obj).equals(any());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "import static org.mockito.Mockito.when;",
                        "class Test {",
                        "  void f() {",
                        "    Object obj = mock(Object.class);",
                        "    when(obj.equals(any())).thenReturn(true).thenReturn(false);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMockWithMultiArgMatcher() {
        fix().addInputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "class Test {",
                        "  void f() {",
                        "    String str = mock(String.class);",
                        "    doReturn(\"result\").when(str).formatted(any(), any());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.mockito.ArgumentMatchers.any;",
                        "import static org.mockito.Mockito.doReturn;",
                        "import static org.mockito.Mockito.mock;",
                        "import static org.mockito.Mockito.when;",
                        "class Test {",
                        "  void f() {",
                        "    String str = mock(String.class);",
                        "    when(str.formatted(any(), any())).thenReturn(\"result\");",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(PreferTypeSafeStubbing.class, getClass());
    }
}
