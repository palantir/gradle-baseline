/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.junit;

import com.palantir.gradle.junit.BuildJunitReportService.ReportFile;
import java.io.File;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public abstract class BuildJunitReportService
        implements BuildService<ReportFile>, OperationCompletionListener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BuildJunitReportService.class);
    private final List<Report.TestCase> testCases = new ArrayList<>();
    private final long startTimeNanos = System.nanoTime();

    public static class ReportFile implements BuildServiceParameters, Serializable {
        private File reportFile;

        public final void setReportFile(File reportFile) {
            this.reportFile = reportFile;
        }
    }

    @Override
    public final synchronized void onFinish(FinishEvent finishEvent) {
        if (!(finishEvent instanceof TaskFinishEvent)) {
            return;
        }
        TaskFinishEvent taskFinishEvent = (TaskFinishEvent) finishEvent;
        TaskOperationResult result = taskFinishEvent.getResult();
        String taskName = taskFinishEvent.getDescriptor().getTaskPath();
        Report.TestCase.Builder testCase = new Report.TestCase.Builder().name(taskName);

        if (result instanceof TaskFailureResult) {
            TaskFailureResult failure = (TaskFailureResult) result;
            List<? extends org.gradle.tooling.Failure> failures = failure.getFailures();
            if (failures.isEmpty()) {
                testCase.failure(new Report.Failure.Builder()
                        .message("operation failed without any specific failure information")
                        .details("")
                        .build());
            } else {
                if (failures.size() != 1) {
                    log.warn("task had multiple failures, which is unexpected. Reporting the first one");
                }
                org.gradle.tooling.Failure firstFailure = failures.iterator().next();
                testCase.failure(new Report.Failure.Builder()
                        .message(firstFailure.getMessage())
                        .details(firstFailure.getDescription())
                        .build());
            }
        }
        testCases.add(testCase.build());
    }

    private Path getTargetFile() {
        return getParameters().reportFile.toPath();
    }

    @Override
    public final synchronized void close() throws Exception {
        Report report = new Report.Builder()
                .name("gradle")
                .subname("gradle")
                .elapsedTimeNanos(System.nanoTime() - startTimeNanos)
                .addAllTestCases(testCases)
                .build();
        Document xml = JunitReportCreator.reportToXml(report);

        Files.createDirectories(getTargetFile().getParent());

        try (Writer writer = Files.newBufferedWriter(getTargetFile(), StandardCharsets.UTF_8)) {
            XmlUtils.write(writer, xml);
        }
    }

    public final List<Report.TestCase> getTestCases() {
        return testCases;
    }
}
