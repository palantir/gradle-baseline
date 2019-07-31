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

import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.Task;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.FindBugs;
import org.gradle.api.tasks.TaskState;
import org.gradle.api.tasks.compile.JavaCompile;

public final class StyleTaskTimer implements TaskTimer {

    private final Map<Task, Long> taskTimeNanosByTask = new LinkedHashMap<>();
    private long lastStartTime;

    @Override
    public long getTaskTimeNanos(Task styleTask) {
        if (!isStyleTask(styleTask)) {
            throw new ClassCastException("not a style task");
        }
        Long taskTimeNanos = taskTimeNanosByTask.get(styleTask);
        if (taskTimeNanos == null) {
            throw new SafeIllegalArgumentException("no time available for task");
        }
        return taskTimeNanos;
    }

    @Override
    public void beforeExecute(Task task) {
        if (isStyleTask(task)) {
            lastStartTime = System.nanoTime();
        }
    }

    @Override
    public void afterExecute(Task task, TaskState taskState) {
        if (isStyleTask(task)) {
            taskTimeNanosByTask.put(task, System.nanoTime() - lastStartTime);
        }
    }

    public static boolean isStyleTask(Task task) {
        return task instanceof Checkstyle || task instanceof FindBugs || task instanceof JavaCompile;
    }
}
