/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.TaskContainer;

public final class Tasks {

    static <T extends Task> T createTask(TaskContainer tasks, String preferredName, Class<T> type) {
        String name = preferredName;
        int count = 1;
        while (true) {
            try {
                Task existingTask = tasks.getByName(name);
                if (type.isInstance(existingTask)) {
                    return null;
                }
            } catch (UnknownTaskException e) {
                return tasks.create(name, type);
            }
            count++;
            name = preferredName + count;
        }
    }

    private Tasks() {}
}
