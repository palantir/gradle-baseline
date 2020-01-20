/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

public abstract class PreconditionsTests {

    public abstract CompilationTestHelper compilationHelper();

    public final void failGuava(String diagnostic, String precondition) {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.base.Preconditions;",
                        "class Test {",
                        "  void f(String param) {",
                        "    // BUG: Diagnostic contains: " + diagnostic,
                        "    " + precondition,
                        "  }",
                        "}")
                .doTest();
    }

    public final void failLogSafe(String diagnostic, String precondition) {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.Preconditions;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "class Test {",
                        "  void f(String param) {",
                        "    // BUG: Diagnostic contains: " + diagnostic,
                        "    " + precondition,
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public final void validGuava() throws Exception {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.base.Preconditions;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  void f(boolean bArg, int iArg, Object oArg) {",
                        "    Preconditions.checkArgument(bArg);",
                        "    Preconditions.checkArgument(bArg, \"message\");",
                        "    Preconditions.checkArgument(bArg, \"message %s\", 'a');",
                        "    Preconditions.checkArgument(bArg, \"message %s\", 123);",
                        "    Preconditions.checkArgument(bArg, \"message %s\", 123L);",
                        "    Preconditions.checkArgument(bArg, \"message %s\", \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 'a', 'b');",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 'a', 123);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 'a', 123L);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 'a', \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123, 'a');",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123, 123);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123, 123L);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123, \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123L, 'a');",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123L, 123);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123L, 123L);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", 123L, \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", \"msg\", 'a');",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", \"msg\", 123);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", \"msg\", 123L);",
                        "    Preconditions.checkArgument(bArg, \"message %s %s\", \"msg\", \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s %s\", \"msg\", \"msg\", \"msg\");",
                        "    Preconditions.checkArgument(bArg, \"message %s %s %s %s\","
                                + " \"msg\", \"msg\", \"msg\", \"msg\");",
                        "",
                        "    Preconditions.checkState(iArg > 0);",
                        "    Preconditions.checkState(iArg > 0, \"message\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s\", 'a');",
                        "    Preconditions.checkState(iArg > 0, \"message %s\", 123);",
                        "    Preconditions.checkState(iArg > 0, \"message %s\", 123L);",
                        "    Preconditions.checkState(iArg > 0, \"message %s\", \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 'a', 'b');",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 'a', 123);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 'a', 123L);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 'a', \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123, 'a');",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123, 123);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123, 123L);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123, \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123L, 'a');",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123L, 123);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123L, 123L);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", 123L, \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", \"msg\", 'a');",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", \"msg\", 123);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", \"msg\", 123L);",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s\", \"msg\", \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s %s\", \"msg\", \"msg\", \"msg\");",
                        "    Preconditions.checkState(iArg > 0, \"message %s %s %s %s\","
                                + " \"msg\", \"msg\", \"msg\", \"msg\");",
                        "",
                        "    Preconditions.checkNotNull(oArg);",
                        "    Preconditions.checkNotNull(oArg, \"message\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s\", 'a');",
                        "    Preconditions.checkNotNull(oArg, \"message %s\", 123);",
                        "    Preconditions.checkNotNull(oArg, \"message %s\", 123L);",
                        "    Preconditions.checkNotNull(oArg, \"message %s\", \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 'a', 'b');",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 'a', 123);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 'a', 123L);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 'a', \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123, 'a');",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123, 123);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123, 123L);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123, \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123L, 'a');",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123L, 123);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123L, 123L);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", 123L, \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", \"msg\", 'a');",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", \"msg\", 123);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", \"msg\", 123L);",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s\", \"msg\", \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s %s\", \"msg\", \"msg\", \"msg\");",
                        "    Preconditions.checkNotNull(oArg, \"message %s %s %s %s\","
                                + " \"msg\", \"msg\", \"msg\", \"msg\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public final void validLogSafe() throws Exception {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.Preconditions;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  void f(boolean bArg, int iArg, Object oArg) {",
                        "    Preconditions.checkArgument(bArg);",
                        "    Preconditions.checkArgument(bArg, \"message\");",
                        "    Preconditions.checkArgument(bArg, \"message {char}\", UnsafeArg.of(\"char\", 'a'));",
                        "    Preconditions.checkArgument(bArg, \"message {char1 {char2}\", UnsafeArg.of(\"char1\","
                                + " 'a'), UnsafeArg.of(\"char2\", 'b'));",
                        "",
                        "    Preconditions.checkState(iArg > 0);",
                        "    Preconditions.checkState(iArg > 0, \"message\");",
                        "    Preconditions.checkState(iArg > 0, \"message {char}\", UnsafeArg.of(\"char\", 'a'));",
                        "    Preconditions.checkState(iArg > 0, \"message {char1} {char2}\", UnsafeArg.of(\"char1\","
                                + " 'a'), UnsafeArg.of(\"char2\", 'b'));",
                        "",
                        "    Preconditions.checkNotNull(oArg);",
                        "    Preconditions.checkNotNull(oArg, \"message\");",
                        "  }",
                        "}")
                .doTest();
    }
}
