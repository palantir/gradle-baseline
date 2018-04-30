/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.tasks.CheckUniqueClassNamesTask;
import org.gradle.api.Project;

@SuppressWarnings("checkstyle:designforextension") // making this 'final' breaks gradle
public class BaselineClasspathDuplicatesPlugin extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        project.getPlugins().withId("java", plugin -> {

            CheckUniqueClassNamesTask task = project.getTasks().create("checkUniqueClassNames",
                    CheckUniqueClassNamesTask.class);
            task.setConfiguration(project.getConfigurations().getByName("testRuntime"));

            project.getTasks().getByName("check").dependsOn(task);

        });
    }
}
