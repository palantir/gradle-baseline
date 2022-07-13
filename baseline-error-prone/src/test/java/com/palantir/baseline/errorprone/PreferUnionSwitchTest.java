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

    // class Sample {
    //     public static boolean isAssetInfo(SampleConjureUnion myUnion) {
    //         return switch (myUnion) {
    //             case SampleConjureUnion.Bar bar -> false;
    //             case SampleConjureUnion.Baz baz -> false;
    //             case SampleConjureUnion.Foo foo -> true;
    //             case SampleConjureUnion.Unknown unknown -> false;
    //         };
    //     }
    // }
}
