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

    private static final String VERSION_RESOURCE = "/version.txt";

    void apply(Project rootProject) {
        this.project = rootProject
        if (rootProject != rootProject.rootProject) {
            throw new IllegalArgumentException(
                    BaselineConfig.canonicalName + " plugin can only be applied to the root project.")
        }

        rootProject.configurations {
            baseline
        }

        def baselineVersion = extractVersionString()
        rootProject.dependencies {
            baseline "com.palantir:gradle-baseline-java-config:${baselineVersion}@zip"
        }

        // Create task for generating configuration.
        def baselineUpdateConfig = rootProject.task(
            "baselineUpdateConfig",
            group: "Baseline",
            description: "Installs or updates Baseline configuration files in .baseline/")
        baselineUpdateConfig.doLast {
            rootProject.copy {
                from project.zipTree(rootProject.configurations.baseline.getSingleFile())
                into getConfigDir()
            }
        }
    }

    private static String extractVersionString() {
        return this.getClass().getResource(VERSION_RESOURCE).text
    }
}
