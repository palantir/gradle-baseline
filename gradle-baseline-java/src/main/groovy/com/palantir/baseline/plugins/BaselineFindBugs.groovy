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
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin

import java.nio.file.Paths

/**
 * Configures the Gradle 'findbugs' task with Baseline-specific configuration.
 */
class BaselineFindBugs extends AbstractBaselinePlugin {

    private static final String DEFAULT_FINDBUGS_VERSION = '3.0.1'
    private static final String DEFAULT_EFFORT = 'max'
    private static final String ANTIPATTERNS_ARTIFACT = 'com.palantir.antipatterns:antipatterns:1.0-beta.4'
    private static final String SLF4J_FINDBUGS_PLUGIN = 'jp.skypencil.findbugs.slf4j:bug-pattern:1.2.4@jar'
    private static final String[] OMITTED_VISITORS = ["StaticLoggerDetector"]

    void apply(Project project) {
        this.project = project

        project.plugins.apply FindBugsPlugin

        // Set report type.
        // We do this at 'apply' time so that they could be overridden by a user later.
        // 'html' is human-readable; 'xml' can be read by the Eclipse FindBugs plugin.
        // Only one can be enabled at a time.
        // Note: This only affects FindBugs tasks that exist when this plugin is applied.
        project.tasks.withType(FindBugs) {
            reports {
                xml.enabled = false
                html.enabled = true
            }
        }

        // Configure not in afterEvaluate so that user can override.
        configureFindBugs()

        project.afterEvaluate { Project p ->
            configureFindBugsForEclipse()
        }
    }

    def configureFindBugs() {
        project.logger.debug("Baseline: Configuring FindBugs tasks")

        // Configure findbugs
        project.findbugs {
            toolVersion = DEFAULT_FINDBUGS_VERSION
            excludeFilter = excludeFilterFile
            effort = DEFAULT_EFFORT
            omitVisitors = OMITTED_VISITORS
        }

        project.dependencies {
            findbugsPlugins ANTIPATTERNS_ARTIFACT
            findbugsPlugins SLF4J_FINDBUGS_PLUGIN
        }
    }

    // Configure checkstyle settings for Eclipse
    def configureFindBugsForEclipse() {
        if (!project.plugins.findPlugin(EclipsePlugin)) {
            project.logger.debug "Baseline: Skipping configuring Eclipse for FindBugs (eclipse not applied)"
            return
        }
        project.logger.debug "Baseline: Configuring Eclipse FindBugs"
        project.eclipse.project {
            natures "edu.umd.cs.findbugs.plugin.eclipse.findbugsNature"
            buildCommand "edu.umd.cs.findbugs.plugin.eclipse.findbugsBuilder"
        }
    }

    File getExcludeFilterFile() {
        project.file(Paths.get(configDir, "findbugs", "excludeFilter.xml").toString())
    }
}
