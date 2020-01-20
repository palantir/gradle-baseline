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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FailuresReportGenerator {

    private FailuresReportGenerator() {}

    private static final Pattern JAVA_FILE_RX = Pattern.compile(".*src/\\w+/java/(.*)\\.java");

    public static Report failuresReport(
            File rootDir, String projectName, String taskName, long elapsedTimeNanos, List<Failure> failures) {
        Report.Builder report = new Report.Builder()
                .elapsedTimeNanos(elapsedTimeNanos)
                .name(projectName)
                .subname(taskName);

        for (Failure failure : failures) {
            String shortSource = failure.source().isEmpty()
                    ? ""
                    : failure.source().replaceAll(".*\\.", "") + " - ";
            String className = getClassName(failure.file());

            Report.TestCase testCase = new Report.TestCase.Builder()
                    .name(shortSource + className)
                    .failure(new Report.Failure.Builder()
                            .message(failure.file().getName() + ":" + failure.line() + ": " + failure.message())
                            .details(failure.severity()
                                    + ": "
                                    + failure.message()
                                    + failure.details()
                                    + "\n"
                                    + (failure.source().isEmpty() ? "" : "Category: " + failure.source() + "\n")
                                    + "File: "
                                    + relativise(rootDir, failure)
                                    + "\n"
                                    + "Line: "
                                    + failure.line()
                                    + "\n")
                            .build())
                    .build();
            report.addTestCases(testCase);
        }

        return report.build();
    }

    public static Path relativise(File rootDir, Failure failure) {
        try {
            return rootDir.toPath().relativize(failure.file().toPath());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Could not relativise " + failure.file() + " wrt " + rootDir, e);
        }
    }

    private static String getClassName(File file) {
        Matcher matcher = JAVA_FILE_RX.matcher(file.toString());
        if (matcher.matches()) {
            return matcher.group(1).replace('/', '.');
        }
        return file.toString();
    }
}
