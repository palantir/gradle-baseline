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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.TaskState;
import org.gradle.api.tasks.testing.Test;

public final class BuildFailureListener implements TaskExecutionListener {

    private final List<Report.TestCase> testCases = new ArrayList<>();

    @Override
    @SuppressWarnings("StrictUnusedVariable")
    public void beforeExecute(Task task) {}

    @Override
    public synchronized void afterExecute(Task task, TaskState state) {
        if (isUntracked(task)) {
            Report.TestCase.Builder testCase =
                    new Report.TestCase.Builder().name(":" + task.getProject().getName() + ":" + task.getName());

            Throwable failure = state.getFailure();
            if (failure != null && isUntracked(task)) {
                if (failure instanceof TaskExecutionException && failure.getCause() != null) {
                    failure = failure.getCause();
                }
                StringWriter stackTrace = new StringWriter();
                failure.printStackTrace(new PrintWriter(stackTrace));

                testCase.failure(new Report.Failure.Builder()
                        .message(getMessage(failure))
                        .details(stackTrace.toString())
                        .build());
            }
            testCases.add(testCase.build());
        }
    }

    public List<Report.TestCase> getTestCases() {
        return testCases;
    }

    private static String getMessage(Throwable throwable) {
        if (throwable.getMessage() == null) {
            return throwable.getClass().getSimpleName();
        } else {
            return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
    }

    private static boolean isUntracked(Task task) {
        return !(task instanceof Test) && !StyleTaskTimer.isStyleTask(task);
    }
}
