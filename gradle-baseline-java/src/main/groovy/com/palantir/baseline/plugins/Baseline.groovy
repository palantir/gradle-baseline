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

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Plugin that configures a project with all Baseline settings.
 */
class Baseline implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply BaselineCheckstyle
        project.plugins.apply BaselineConfig
        project.plugins.apply BaselineEclipse
        project.plugins.apply BaselineIdea
        project.plugins.apply BaselineErrorProne
        project.plugins.apply BaselineClasspathConflict
    }
}
