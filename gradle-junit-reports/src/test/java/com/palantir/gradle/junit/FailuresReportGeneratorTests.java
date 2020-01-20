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

import static com.palantir.gradle.junit.FailuresReportGenerator.failuresReport;
import static com.palantir.gradle.junit.TestCommon.CHECKSTYLE_FAILURES;
import static com.palantir.gradle.junit.TestCommon.FAILED_CHECKSTYLE_TIME_NANOS;
import static com.palantir.gradle.junit.TestCommon.REPORT;
import static com.palantir.gradle.junit.TestCommon.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.junit.Test;

public final class FailuresReportGeneratorTests {

    @Test
    public void testNoErrors() {
        Report report = failuresReport(
                ROOT, "fooproject", "checkstyleTest", FAILED_CHECKSTYLE_TIME_NANOS, ImmutableList.<Failure>of());
        assertThat(report).isEqualTo(new Report.Builder()
                .name("fooproject")
                .subname("checkstyleTest")
                .elapsedTimeNanos(FAILED_CHECKSTYLE_TIME_NANOS)
                .build());
    }

    @Test
    public void testTwoErrors() {
        Report report =
                failuresReport(ROOT, "fooproject", "checkstyleTest", FAILED_CHECKSTYLE_TIME_NANOS, CHECKSTYLE_FAILURES);
        assertThat(report).isEqualTo(REPORT);
    }

    @Test
    public void testJavacErrors() {
        List<Failure> failures = ImmutableList.of(
                new Failure.Builder()
                        .file(new File(ROOT, "src/main/java/com/example/MyClass.java"))
                        .line(8)
                        .severity("ERROR")
                        .message("incompatible types: String cannot be converted to int")
                        .details("\n    private final int a = \"hello\";                               "
                                + "\n                          ^")
                        .build(),
                new Failure.Builder()
                        .file(new File(ROOT, "src/main/java/com/example/MyClass.java"))
                        .line(12)
                        .severity("ERROR")
                        .message("cannot assign a value to final variable b")
                        .details("\n        b = 2;                                                   "
                                + "\n        ^                                                        ")
                        .build());
        Report report = failuresReport(ROOT, "foobar", "compileJava", 293_000, failures);
        assertThat(report).isEqualTo(new Report.Builder()
                .name("foobar")
                .subname("compileJava")
                .elapsedTimeNanos(293_000)
                .addTestCases(new Report.TestCase.Builder()
                        .name("com.example.MyClass")
                        .failure(new Report.Failure.Builder()
                                .message("MyClass.java:8: incompatible types: String cannot be converted to int")
                                .details("ERROR: incompatible types: String cannot be converted to int\n"
                                        + "    private final int a = \"hello\";                               \n"
                                        + "                          ^\n"
                                        + "File: src/main/java/com/example/MyClass.java\n"
                                        + "Line: 8\n")
                                .build())
                        .build())
                .addTestCases(new Report.TestCase.Builder()
                        .name("com.example.MyClass")
                        .failure(new Report.Failure.Builder()
                                .message("MyClass.java:12: cannot assign a value to final variable b")
                                .details("ERROR: cannot assign a value to final variable b\n"
                                        + "        b = 2;                                                   \n"
                                        + "        ^                                                        \n"
                                        + "File: src/main/java/com/example/MyClass.java\n"
                                        + "Line: 12\n")
                                .build())
                        .build())
                .build());
    }
}
