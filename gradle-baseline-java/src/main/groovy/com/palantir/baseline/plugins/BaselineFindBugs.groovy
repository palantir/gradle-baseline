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

import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.palantir.baseline.BaselineFindBugsExtension
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

    void apply(Project project) {
        this.project = project
        BaselineFindBugsExtension extension = project.extensions.create("baselineFindbugs", BaselineFindBugsExtension)

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
        configureFindBugs(extension)

        project.afterEvaluate { Project p ->
            configureFindBugsForEclipse()
        }
    }

    def configureFindBugs(BaselineFindBugsExtension extension) {
        project.logger.info("Baseline: Configuring FindBugs tasks")

        // Configure findbugs
        project.findbugs {
            toolVersion = DEFAULT_FINDBUGS_VERSION
            excludeFilter = excludeFilterFile
            effort = DEFAULT_EFFORT
        }

        project.tasks.withType(FindBugs, { task ->
            def filter = { File file ->
                String javaFile = new File(file.path
                        .replaceFirst(/\$\w+\.class$/, '')
                        .replaceFirst(/\.class$/, '')
                        + '.java').absolutePath

                boolean keepFile = !extension.exclusions.any { javaFile =~ it }
                return keepFile
            }

            // FindBugs fails if run on an empty set of classes. Need to disable the task before it's run.
            task.onlyIf {
                def filteredClasses = task.classes.filter filter
                return !filteredClasses.empty
            }

            task.doFirst {
                task.classes = task.classes.filter filter
            }
        })
    }

    // Configure checkstyle settings for Eclipse
    def configureFindBugsForEclipse() {
        if (!project.plugins.findPlugin(EclipsePlugin)) {
            project.logger.info "Baseline: Skipping configuring Eclipse for FindBugs (eclipse not applied)"
            return
        }
        project.logger.info "Baseline: Configuring Eclipse FindBugs"
        project.eclipse.project {
            natures "edu.umd.cs.findbugs.plugin.eclipse.findbugsNature"
            buildCommand "edu.umd.cs.findbugs.plugin.eclipse.findbugsBuilder"
        }
    }

    File getExcludeFilterFile() {
        project.file(Paths.get(configDir, "findbugs", "excludeFilter.xml").toString())
    }
}
