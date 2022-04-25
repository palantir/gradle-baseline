/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

class SafeLoggingPropagationTest {

    @Test
    void testAddsAnnotation_dnlType() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  BearerToken token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  BearerToken token();",
                        "}")
                .doTest();
    }

    @Test
    void testMixedSafety() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe String one();",
                        "  @Unsafe String two();",
                        "  String three();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Unsafe",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe String one();",
                        "  @Unsafe String two();",
                        "  String three();",
                        "}")
                .doTest();
    }

    @Test
    void testAddsAnnotation_dnlReturnValue() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testReplacesAnnotation_dnlReturnValue() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Unsafe",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testDoesNotReplaceStrictAnnotation() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "@DoNotLog",
                        "interface Test {",
                        "  @Unsafe",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testRecordWithUnsafeTypes() {
        fix("--release", "15", "--enable-preview")
                .addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "record Test(BearerToken token) {}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "@DoNotLog",
                        "record Test(BearerToken token) {}")
                .doTest();
    }

    @Test
    void testDoesNotAddSafeAnnotation() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresStaticMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe",
                        "  static String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresVoidMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  void token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresMethodsWithParameters() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token(int i);",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresInterfacesWithMethodsWithParameters() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  String token(int i);",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIncludesIfacesWithMethodsWithParametersIfImmutables() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  default String token(int i) { return \"\"; };",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  default String token(int i) { return \"\"; };",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresThrowable() {
        // exceptions are unsafe-by-default, it's unnecessary to annotate every exception as unsafe.
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class MyException extends RuntimeException {",
                        "  @Override public String getMessage() {",
                        "     return super.getMessage();",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresAnonymous() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "import java.util.function.*;",
                        "public final class Test {",
                        "  private static final Supplier<?> supplier = new Supplier<BearerToken>() {",
                        "    @Override public BearerToken get() {",
                        "      return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "    }",
                        "  };",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix(String... args) {
        return RefactoringValidator.of(SafeLoggingPropagation.class, getClass(), args);
    }
}
