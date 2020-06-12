package com.palantir.baseline.errorprone;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.bugpatterns.UnnecessaryParentheses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

public class UnnecessaryParenthesesTest {

    @Test
    @DisabledForJreRange(max = JRE.JAVA_13)
    public void testSwitchExpression() {
        CompilationTestHelper compilationHelper = CompilationTestHelper.newInstance(
                        UnnecessaryParentheses.class, getClass())
                .setArgs(ImmutableList.of("--enable-preview", "--release", "14"));

        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  static int foo(String value) {",
                        "    // BUG: Diagnostic contains: These grouping parentheses are unnecessary",
                        "    return switch (value) {",
                        "      case \"Foo\" -> 10;",
                        "      default -> 0;",
                        "    };",
                        "  }",
                        "}")
                .doTest();
    }
}
