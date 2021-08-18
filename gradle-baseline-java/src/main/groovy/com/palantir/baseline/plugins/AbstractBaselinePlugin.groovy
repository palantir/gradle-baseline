/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The super class of all Baseline plugins.
 *
 * Note that we need to extend {@link GroovyObjectSupport} to still support projects using Gradle 6 and thus Groovy 2.x
 * because Baseline is now using Gradle 7 and thus Groovy 3.x. Otherwise, Groovy plugins (i.e. {@link BaselineIdea})
 * fail when setting properties. For more info, see ADD-PR-LINK.
 */
abstract class AbstractBaselinePlugin extends GroovyObjectSupport implements Plugin<Project> {

    /** The {@link Project} that this plugin has been applied to; must be set in the {@link Project#apply} method. */
    protected Project project

    /** Returns the absolute path of the Baseline configuration, i.e., the directory '.baseline' in the root directory
     * of this project. */
    protected final String getConfigDir() {
        return Paths.get(project.rootDir.toString(), BaselineParameters.DEFAULT_CONFIG_DIR).toString()
    }
}
