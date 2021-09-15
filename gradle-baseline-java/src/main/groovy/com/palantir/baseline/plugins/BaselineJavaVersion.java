/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.extensions.BaselineJavaVersionExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

public final class BaselineJavaVersion implements Plugin<Project> {

    public static final String EXTENSION_NAME = "javaVersion";

    @Override
    public void apply(Project project) {
        BaselineJavaVersionExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionExtension.class, project);
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            if (extension.target().isPresent()) {
                String stringValue = extension.target().get().toString();
                task.setSourceCompatibility(stringValue);
                task.setTargetCompatibility(stringValue);
            }
        });
    }
}
