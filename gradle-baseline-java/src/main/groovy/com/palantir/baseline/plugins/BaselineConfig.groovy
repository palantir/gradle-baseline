/*
 * Copyright 2015 Palantir Technologies, Inc.
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

package com.palantir.baseline.plugins

import org.gradle.api.Project

/**
 * Extracts Baseline configuration into the configuration directory.
 */
class BaselineConfig extends AbstractBaselinePlugin {

    void apply(Project rootProject) {
        this.project = rootProject
        if (rootProject != rootProject.rootProject) {
            throw new IllegalArgumentException(
                    BaselineConfig.canonicalName + " plugin can only be applied to the root project.")
        }

        rootProject.configurations {
            baseline
        }

        // Create task for generating configuration.
        def baselineUpdateConfig = rootProject.task(
            "baselineUpdateConfig",
            group: "Baseline",
            description: "Installs or updates Baseline configuration files in .baseline/")
        baselineUpdateConfig.doLast {
            def configFiles = rootProject.configurations.baseline
            if (configFiles.files.size() != 1) {
                throw new IllegalArgumentException("Expected to find exactly one config dependency in the " +
                        "'baseline' configuration, found: " + configFiles.files)
            }

            rootProject.copy {
                from project.zipTree(configFiles.singleFile)
                into getConfigDir()
            }
        }
    }
}
