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

package com.palantir.gradle.junit;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;

public class JunitReportsExtension {
    private static final String EXT_JUNIT_REPORTS = "junitReports";

    private final DirectoryProperty reportsDirectory;
    private final TaskTimer timer;

    static JunitReportsExtension register(Project project) {
        TaskTimer timer = new StyleTaskTimer();
        project.getGradle().addListener(timer);
        return project.getExtensions().create(EXT_JUNIT_REPORTS, JunitReportsExtension.class, timer);
    }

    public JunitReportsExtension(Project project, TaskTimer timer) {
        this.reportsDirectory = project.getObjects()
                .directoryProperty()
                .value(project.getLayout().getBuildDirectory().dir("junit-reports"));
        this.timer = timer;
    }

    public final DirectoryProperty getReportsDirectory() {
        return reportsDirectory;
    }

    public final TaskTimer getTimer() {
        return this.timer;
    }
}
