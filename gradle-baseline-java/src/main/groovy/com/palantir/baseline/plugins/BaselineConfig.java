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

import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Extracts Baseline configuration into the configuration directory.
 */
class BaselineConfig extends AbstractBaselinePlugin {

    public void apply(Project rootProject) {
        this.project = rootProject;

        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new IllegalArgumentException(
                    BaselineConfig.class.getCanonicalName() + " plugin can only be applied to the root project.");
        }

        Configuration configuration = rootProject.getConfigurations().create("baseline");

        // users can still override this default dependency, it just reduces boilerplate
        Optional<String> version = Optional.ofNullable(getClass().getPackage().getImplementationVersion());
        configuration.defaultDependencies(d -> d.add(rootProject.getDependencies().create(String.format(
                "com.palantir.baseline:gradle-baseline-java-config%s@zip", version.map(v -> ":" + v).orElse("")))));

        // Create task for generating configuration.
        rootProject.getTasks().register("baselineUpdateConfig", task -> {
            task.setGroup("Baseline");
            task.setDescription("Installs or updates Baseline configuration files in .baseline/");
            task.getInputs().files(configuration);
            task.getOutputs().dir(getConfigDir());
            task.getOutputs().dir(rootProject.getRootDir().toPath().resolve("project"));
            task.doLast(t -> {
                if (configuration.getFiles().size() != 1) {
                    throw new IllegalArgumentException("Expected to find exactly one config dependency in the "
                            + "'baseline' configuration, found: " + configuration.getFiles());
                }

                rootProject.copy(copySpec -> {
                    copySpec.from(rootProject.zipTree(configuration.getSingleFile()));
                    copySpec.into(getConfigDir());
                    copySpec.exclude("**/scalastyle_config.xml");
                    copySpec.setIncludeEmptyDirs(false);
                });
                if (rootProject.getAllprojects().stream().anyMatch(p -> p.getPluginManager().hasPlugin("scala"))) {
                    rootProject.copy(copySpec -> {
                        copySpec.from(rootProject.zipTree(configuration.getSingleFile())
                                .filter(file -> file.getName().equals("scalastyle_config.xml")));
                        copySpec.into(rootProject.getRootDir().toPath().resolve("project"));
                        copySpec.setIncludeEmptyDirs(false);
                    });
                }
            });
        });
    }
}
