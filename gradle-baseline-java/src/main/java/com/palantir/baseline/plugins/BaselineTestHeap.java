/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

/**
 * Increases the default {@code test} task heap from {@code 512m} to {@code 2g}.
 */
public final class BaselineTestHeap implements Plugin<Project> {

    private static final String TEST_HEAP = "2g";

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class).configureEach(testTask -> {
            String currentValue = testTask.getMaxHeapSize();
            if (currentValue == null) {
                testTask.setMaxHeapSize(TEST_HEAP);
            }
        });
    }
}
