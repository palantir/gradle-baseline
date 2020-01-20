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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.palantir.gradle.junit.Report.TestCase;
import org.gradle.api.Task;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.TaskState;
import org.junit.Test;
import org.mockito.Mockito;

public class BuildFailureListenerTests {

    private static final String PROJECT_1_NAME = "project1";
    private static final String TASK_1_NAME = "task1";
    private static final String PROJECT_2_NAME = "project2";
    private static final String TASK_2_NAME = "task2";

    private final BuildFailureListener listener = new BuildFailureListener();

    @Test
    public void noTasks() {
        assertThat(listener.getTestCases()).isEmpty();
    }

    @Test
    public void onlyTestAndStyleTasks() {
        listener.afterExecute(mock(org.gradle.api.tasks.testing.Test.class), succeeded());
        listener.afterExecute(mock(Checkstyle.class), succeeded());
        assertThat(listener.getTestCases()).isEmpty();
    }

    @Test
    public void successfulTasks() {
        listener.afterExecute(task(PROJECT_1_NAME, TASK_1_NAME), succeeded());
        listener.afterExecute(task(PROJECT_2_NAME, TASK_2_NAME), succeeded());
        assertThat(listener.getTestCases())
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
        listener.afterExecute(task(PROJECT_1_NAME, TASK_1_NAME), failed("task 1 failed"));
        listener.afterExecute(task(PROJECT_2_NAME, TASK_2_NAME), failed("task 2 failed"));

        assertThat(listener.getTestCases()).hasSize(2);

        TestCase testCase1 = listener.getTestCases().get(0);
        assertThat(testCase1.name()).isEqualTo(":" + PROJECT_1_NAME + ":" + TASK_1_NAME);
        assertThat(testCase1.failure()).isNotNull();
        assertThat(testCase1.failure().message()).isEqualTo("RuntimeException: task 1 failed");
        assertThat(testCase1.failure().details())
                .startsWith("java.lang.RuntimeException: task 1 failed\n"
                        + "\tat "
                        + BuildFailureListenerTests.class.getName()
                        + ".failed");

        TestCase testCase2 = listener.getTestCases().get(1);
        assertThat(testCase2.name()).isEqualTo(":" + PROJECT_2_NAME + ":" + TASK_2_NAME);
        assertThat(testCase2.failure()).isNotNull();
        assertThat(testCase2.failure().message()).isEqualTo("RuntimeException: task 2 failed");
        assertThat(testCase2.failure().details())
                .startsWith("java.lang.RuntimeException: task 2 failed\n"
                        + "\tat "
                        + BuildFailureListenerTests.class.getName()
                        + ".failed");
    }

    private static Task task(String projectName, String taskName) {
        Task task = Mockito.mock(Task.class, Mockito.RETURNS_DEEP_STUBS);
        when(task.getProject().getName()).thenReturn(projectName);
        when(task.getName()).thenReturn(taskName);
        return task;
    }

    private static TaskState succeeded() {
        org.gradle.api.internal.tasks.TaskStateInternal state = new org.gradle.api.internal.tasks.TaskStateInternal();
        state.setOutcome(org.gradle.api.internal.tasks.TaskExecutionOutcome.EXECUTED);
        return state;
    }

    private static TaskState failed(String message) {
        org.gradle.api.internal.tasks.TaskStateInternal state = new org.gradle.api.internal.tasks.TaskStateInternal();
        state.setOutcome(org.gradle.api.internal.tasks.TaskExecutionOutcome.EXECUTED);
        state.setOutcome(new RuntimeException(message));
        return state;
    }
}
