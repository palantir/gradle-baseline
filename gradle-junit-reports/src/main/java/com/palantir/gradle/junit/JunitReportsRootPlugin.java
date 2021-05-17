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

package com.palantir.gradle.junit;

import com.google.common.base.Preconditions;
import java.nio.file.Path;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

public final class JunitReportsRootPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Preconditions.checkState(
                project.getRootProject().equals(project), "Plugin must only be applied to root project");

        JunitReportsExtension reportsExtension = JunitReportsExtension.register(project);
        configureBuildFailureFinalizer(project.getRootProject(), reportsExtension.getReportsDirectory());
    }

    private static void configureBuildFailureFinalizer(Project rootProject, Provider<Directory> reportsDir) {
        Provider<RegularFile> targetFileProvider = reportsDir.map(dir -> {
            int attemptNumber = 1;
            Path targetFile = dir.getAsFile().toPath().resolve("gradle").resolve("build.xml");
            while (targetFile.toFile().exists()) {
                targetFile = dir.getAsFile().toPath().resolve("gradle").resolve("build" + ++attemptNumber + ".xml");
            }
            return dir.file(targetFile.toAbsolutePath().toString());
        });

        BuildFailureListener listener = new BuildFailureListener();
        BuildFinishedAction action = new BuildFinishedAction(targetFileProvider, listener);
        rootProject.getGradle().addListener(listener);
        rootProject.getGradle().buildFinished(action);
    }
}
