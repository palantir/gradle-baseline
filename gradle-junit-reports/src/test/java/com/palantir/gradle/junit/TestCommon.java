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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TestCommon {

    private static final String SOURCE = "com.puppycrawl.tools.checkstyle.checks.naming.ParameterNameCheck";
    public static final File ROOT = new File("/home/ubuntu/fooproject");
    private static final String CLASSFILE = "fooproject/src/main/java/org/example/server/FooApplication.java";
    private static final String MESSAGE_2 = "Parameter name 'c' must match pattern '^[a-z][a-zA-Z0-9][a-zA-Z0-9]*$'.";
    private static final String MESSAGE_1 = "Parameter name 'b' must match pattern '^[a-z][a-zA-Z0-9][a-zA-Z0-9]*$'.";
    public static final ImmutableList<Failure> CHECKSTYLE_FAILURES = ImmutableList.of(
            new Failure.Builder()
                    .source(SOURCE)
                    .severity("ERROR")
                    .file(new File(ROOT, CLASSFILE))
                    .line(135)
                    .message(MESSAGE_1)
                    .build(),
            new Failure.Builder()
                    .source(SOURCE)
                    .severity("ERROR")
                    .file(new File(ROOT, CLASSFILE))
                    .line(181)
                    .message(MESSAGE_2)
                    .build());
    public static final long FAILED_CHECKSTYLE_TIME_NANOS = 321_000_000_000L;
    public static final Report REPORT = new Report.Builder()
            .name("fooproject")
            .subname("checkstyleTest")
            .elapsedTimeNanos(FAILED_CHECKSTYLE_TIME_NANOS)
            .addTestCases(new Report.TestCase.Builder()
                    .name("ParameterNameCheck - org.example.server.FooApplication")
                    .failure(new Report.Failure.Builder()
                            .message("FooApplication.java:135: " + MESSAGE_1)
                            .details("ERROR: "
                                    + MESSAGE_1
                                    + "\n"
                                    + "Category: "
                                    + SOURCE
                                    + "\n"
                                    + "File: "
                                    + CLASSFILE
                                    + "\n"
                                    + "Line: 135\n")
                            .build())
                    .build())
            .addTestCases(new Report.TestCase.Builder()
                    .name("ParameterNameCheck - org.example.server.FooApplication")
                    .failure(new Report.Failure.Builder()
                            .message("FooApplication.java:181: " + MESSAGE_2)
                            .details("ERROR: "
                                    + MESSAGE_2
                                    + "\n"
                                    + "Category: "
                                    + SOURCE
                                    + "\n"
                                    + "File: "
                                    + CLASSFILE
                                    + "\n"
                                    + "Line: 181\n")
                            .build())
                    .build())
            .build();

    public static URL testFile(String filename) {
        return Resources.getResource(TestCommon.class, filename);
    }

    public static String readTestFile(String filename) {
        try {
            return Resources.toString(Resources.getResource(TestCommon.class, filename), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TestCommon() {}
}
