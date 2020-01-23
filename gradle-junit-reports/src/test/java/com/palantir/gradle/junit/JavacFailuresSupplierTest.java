/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.Test;

public final class JavacFailuresSupplierTest {

    private static final String CLASS_FILE = "/tmp/ab1/src/main/java/com/example/MyClass.java";
    private static final int LINE_1 = 8;
    private static final String ERROR_1 = "incompatible types: String cannot be converted to int";
    private static final String DETAIL_1 =
            "\n    private final int a = \"hello\";                               " + "\n                          ^";
    private static final int LINE_2 = 12;
    private static final String ERROR_2 = "cannot assign a value to final variable b";
    private static final String DETAIL_2 = "\n        b = 2;                                                   "
            + "\n        ^                                                        ";

    @Test
    public void noFailuresInEmptyOutput() {
        String javacOutput = "";
        JavacFailuresSupplier supplier = new JavacFailuresSupplier(new StringBuilder(javacOutput));
        assertThat(supplier.getFailures()).isEmpty();
    }

    @Test
    public void noFailuresInOutputWithOnlyWarnings() {
        String javacOutput = "warning: [options] bootstrap class path not set in conjunction with -source 1.7\n"
                + "Note: "
                + CLASS_FILE
                + " uses unchecked or unsafe operations.\n"
                + "Note: Recompile with -Xlint:unchecked for details.               \n"
                + "1 warning";
        JavacFailuresSupplier supplier = new JavacFailuresSupplier(new StringBuilder(javacOutput));
        assertThat(supplier.getFailures()).isEmpty();
    }

    @Test
    public void twoFailuresInOutputWithNoWarnings() {
        String javacOutput = CLASS_FILE
                + ":"
                + LINE_1
                + ": error: "
                + ERROR_1
                + DETAIL_1
                + "\n"
                + CLASS_FILE
                + ":"
                + LINE_2
                + ": error: "
                + ERROR_2
                + DETAIL_2
                + "\n";
        JavacFailuresSupplier supplier = new JavacFailuresSupplier(new StringBuilder(javacOutput));
        assertThat(supplier.getFailures())
                .containsExactly(
                        new Failure.Builder()
                                .file(new File(CLASS_FILE))
                                .line(LINE_1)
                                .severity("ERROR")
                                .message(ERROR_1)
                                .details(DETAIL_1)
                                .build(),
                        new Failure.Builder()
                                .file(new File(CLASS_FILE))
                                .line(12)
                                .severity("ERROR")
                                .message(ERROR_2)
                                .details(DETAIL_2)
                                .build());
    }
}
