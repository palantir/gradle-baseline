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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BaselineVersionsProps implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        warnIfAppliedToSubproject(project);

        File versionsPropsFile = createVersionsPropsFileIfNecessary(project);
    }

    private static File createVersionsPropsFileIfNecessary(Project project) {
        File file = project.file("versions.props");
        if (!file.canRead()) {
            try {
                Files.createFile(file.toPath());
            } catch (IOException e) {
                project.getLogger().warn("Unable to create empty versions.props file, please create this manually", e);
            }
        }
        return file;
    }

    private static void warnIfAppliedToSubproject(Project project) {
        if (project != project.getRootProject()) {
            project.getLogger().warn(
                    "com.palantir.baseline-versions-props should be applied to the root project only, not '{}'",
                    project.getName());
        }
    }

}
