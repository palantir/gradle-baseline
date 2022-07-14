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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.jupiter.api.Test;

/** {@link com.palantir.baseline.errorprone.SampleConjureUnion}. */
class PreferUnionSwitchTest {

    /**
     * Cases to test:
     * - staged builders
     * - non staged builders
     * - visitor defined using anonymous class
     * - throwOnUnknown
     * - one line expressions (not involving the variable)
     * - one line expressions (involving the variable)
     * - multiline expressions including return keyword (replace with yield)
     */
    @Test
    public void auto_fix_straightforward_lambdas() {
        RefactoringValidator.of(PreferUnionSwitch.class, getClass(), "--enable-preview", "--release=17")
                .addInputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "class Test {",
                        "public static boolean isAssetInfo(SampleConjureUnion myUnion) {",
                        "    return myUnion.accept(SampleConjureUnion.Visitor.<Boolean>builder()",
                        "            .bar(_bar -> false)",
                        "            .baz(_baz -> false)",
                        "            .foo(_foo -> true)",
                        "            .unknown(_unknown -> false)",
                        "            .build());",
                        "}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "class Test {",
                        "public static boolean isAssetInfo(SampleConjureUnion myUnion) {",
                        "    return switch (myUnion) {",
                        "        case SampleConjureUnion.Bar _bar -> false;",
                        "        case SampleConjureUnion.Baz _baz -> false;",
                        "        case SampleConjureUnion.Foo _foo -> true;",
                        "        case SampleConjureUnion.Unknown _unknown -> false;",
                        "    };",
                        "}",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void auto_fix_complex_one_liners() {
        RefactoringValidator.of(PreferUnionSwitch.class, getClass(), "--enable-preview", "--release=17")
                .addInputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "import java.util.function.Function;",
                        "class Test {",
                        "public static long getLong(SampleConjureUnion myUnion) {",
                        "    return myUnion.accept(SampleConjureUnion.Visitor.<Long>builder()",
                        "            .bar(bar -> (long) bar)",
                        "            .baz(Function.identity())",
                        "            .foo(Long::parseLong)",
                        "            .unknown(_unknown -> 0L)",
                        "            .build());",
                        "}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "import java.util.function.Function;",
                        "class Test {",
                        "public static long getLong(SampleConjureUnion myUnion) {",
                        "    return switch (myUnion) {",
                        "        case SampleConjureUnion.Bar bar -> (long) bar.value();",
                        "        case SampleConjureUnion.Baz baz -> baz.value();",
                        "        case SampleConjureUnion.Foo foo -> Long.parseLong(foo.value());",
                        "        case SampleConjureUnion.Unknown _unknown -> 0L;",
                        "    };",
                        "}",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void auto_fix_anonymous_class() {
        RefactoringValidator.of(PreferUnionSwitch.class, getClass(), "--enable-preview", "--release=17")
                .addInputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "import java.util.function.Function;",
                        "class Test {",
                        "public static long getLong(SampleConjureUnion myUnion) {",
                        "        return myUnion.accept(new SampleConjureUnion.Visitor<Long>() {",
                        "            @Override",
                        "            public Long visitFoo(String value) {",
                        "                return Long.parseLong(value);",
                        "            }",
                        "            @Override",
                        "            public Long visitBar(int value) {",
                        "                return (long) value;",
                        "            }",
                        "            @Override",
                        "            public Long visitBaz(long value) {",
                        "                return value;",
                        "            }",
                        "            @Override",
                        "            public Long visitUnknown(String _unknownType, Object _unknownValue) {",
                        "                return 0L;",
                        "            }",
                        "        });",
                        "}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.baseline.errorprone.SampleConjureUnion;",
                        "import java.util.function.Function;",
                        "class Test {",
                        "public static long getLong(SampleConjureUnion myUnion) {",
                        "    return switch (myUnion) {",
                        "        case SampleConjureUnion.Foo value -> {",
                        "            yield Long.parseLong(value.value());",
                        "        }",
                        "        case SampleConjureUnion.Bar value -> {",
                        "            yield (long) value.value();",
                        "        }",
                        "        case SampleConjureUnion.Baz value -> {",
                        "            yield value.value();",
                        "        }",
                        "        case SampleConjureUnion.Unknown _unknownType -> {",
                        "            yield 0L;",
                        "        }",
                        "    };",
                        "}",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
