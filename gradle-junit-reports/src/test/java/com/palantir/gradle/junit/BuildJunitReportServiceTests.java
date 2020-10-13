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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.palantir.gradle.junit.Report.TestCase;
import org.gradle.internal.build.event.types.DefaultTaskDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.task.TaskExecutionResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
import org.junit.Test;

public class BuildJunitReportServiceTests {

    private static final String PROJECT_1_NAME = "project1";
    private static final String TASK_1_NAME = "task1";
    private static final String PROJECT_2_NAME = "project2";
    private static final String TASK_2_NAME = "task2";

    private final BuildJunitReportService service = new BuildJunitReportService() {
        @SuppressWarnings("OverridesJavaxInjectableMethod")
        @Override
        public ReportFile getParameters() {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void successfulTasks() {
        service.onFinish(task(PROJECT_1_NAME, TASK_1_NAME, succeeded()));
        service.onFinish(task(PROJECT_2_NAME, TASK_2_NAME, succeeded()));
        assertThat(service.getTestCases())
                .containsExactly(
                        new TestCase.Builder()
                                .name(":" + PROJECT_1_NAME + ":" + TASK_1_NAME)
                                .build(),
                        new TestCase.Builder()
                                .name(":" + PROJECT_2_NAME + ":" + TASK_2_NAME)
                                .build());
    }

    @Test
    public void failedTasks() {
        service.onFinish(task(PROJECT_1_NAME, TASK_1_NAME, failed("task 1 failed")));
        service.onFinish(task(PROJECT_2_NAME, TASK_2_NAME, failed("task 2 failed")));

        assertThat(service.getTestCases()).hasSize(2);

        TestCase testCase1 = service.getTestCases().get(0);
        assertThat(testCase1.name()).isEqualTo(":" + PROJECT_1_NAME + ":" + TASK_1_NAME);
        assertThat(testCase1.failure()).isNotNull();
        assertThat(testCase1.failure().message()).isEqualTo("task 1 failed");
        assertThat(testCase1.failure().details())
                .startsWith("java.lang.RuntimeException: task 1 failed\n"
                        + "\tat "
                        + BuildJunitReportServiceTests.class.getName()
                        + ".failed");

        TestCase testCase2 = service.getTestCases().get(1);
        assertThat(testCase2.name()).isEqualTo(":" + PROJECT_2_NAME + ":" + TASK_2_NAME);
        assertThat(testCase2.failure()).isNotNull();
        assertThat(testCase2.failure().message()).isEqualTo("task 2 failed");
        assertThat(testCase2.failure().details())
                .startsWith("java.lang.RuntimeException: task 2 failed\n"
                        + "\tat "
                        + BuildJunitReportServiceTests.class.getName()
                        + ".failed");
    }

    private static TaskFinishEvent task(String projectName, String taskName, TaskExecutionResult result) {
        DefaultTaskDescriptor descriptor = new DefaultTaskDescriptor(null, null, null, null, null, null, null);
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + projectName + ":" + taskName,
                new DefaultTaskOperationDescriptor(
                        descriptor,
                        new DefaultOperationDescriptor(descriptor, null),
                        ":" + projectName + ":" + taskName),
                result);
    }

    private static TaskExecutionResult succeeded() {
        return new DefaultTaskSuccessResult(0, 0, false, false, null);
    }

    private static TaskExecutionResult failed(String message) {
        RuntimeException exception = new RuntimeException(message);
        return new DefaultTaskFailureResult(
                0,
                0,
                ImmutableList.of(org.gradle.tooling.internal.consumer.DefaultFailure.fromThrowable(exception)),
                null);
    }
}
