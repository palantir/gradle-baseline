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

import com.palantir.baseline.BaselineParameters
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Paths

/**
 * The super class of all Baseline plugins.
 */
abstract class AbstractBaselinePlugin implements Plugin<Project> {

    /** The {@link Project} that this plugin has been applied to; must be set in the {@link Project#apply} method. */
    protected Project project

    /** Returns the absolute path of the Baseline configuration, i.e., the directory '.baseline' in the root directory
     * of this project. */
    protected final String getConfigDir() {
        return Paths.get(project.rootDir.toString(), BaselineParameters.DEFAULT_CONFIG_DIR).toString()
    }
}
