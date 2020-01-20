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
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/** Sensible defaults so that all Jar, Tar, Zip tasks can be deterministically reproduced. */
public final class BaselineReproducibility implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(t -> {
            t.setPreserveFileTimestamps(false);
            t.setReproducibleFileOrder(true);
            t.setDuplicatesStrategy(DuplicatesStrategy.WARN);
        });

        project.getPluginManager().withPlugin("nebula.info", plugin -> {
            project.getLogger()
                    .warn(
                            "Please remove the 'nebula.info' plugin from {} as it breaks "
                                    + "reproducibility of jars by adding a 'Build-Date' entry to the MANIFEST.MF",
                            project);
        });
    }
}
