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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;

@SuppressWarnings("deprecation")
public final class DefaultTaskTimer implements TaskTimer, TaskExecutionListener {

    private final Map<Task, Timer> taskTimers = new LinkedHashMap<>();
    private final Predicate<Task> isTrackedTask;

    public DefaultTaskTimer(Predicate<Task> isTrackedTask) {
        this.isTrackedTask = isTrackedTask;
    }

    @Override
    public long getTaskTimeNanos(Task task) {
        return Optional.ofNullable(taskTimers.get(task))
                .map(Timer::getElapsed)
                .orElseThrow(() -> new IllegalArgumentException("No time available for task " + task.getName()));
    }

    @Override
    public void beforeExecute(Task task) {
        if (isTrackedTask.test(task)) {
            taskTimers.put(task, new Timer());
        }
    }

    @Override
    public void afterExecute(Task task, TaskState _taskState) {
        Optional.ofNullable(taskTimers.get(task)).ifPresent(Timer::stop);
    }

    static final class Timer {
        private final long startTime;
        private OptionalLong endTime;

        Timer() {
            this.startTime = System.nanoTime();
            this.endTime = OptionalLong.empty();
        }

        void stop() {
            endTime = OptionalLong.of(System.nanoTime());
        }

        long getElapsed() {
            if (endTime.isPresent()) {
                return endTime.getAsLong() - startTime;
            }
            return 0L;
        }
    }
}
