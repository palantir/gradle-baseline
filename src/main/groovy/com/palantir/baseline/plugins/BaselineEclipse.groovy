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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin

/**
 * Configures the Gradle 'eclipse' task with Baseline settings.
 */
class BaselineEclipse extends AbstractBaselinePlugin {

    void apply(Project project) {
        this.project = project

        project.plugins.apply EclipsePlugin

        project.afterEvaluate { Project p ->
            def eclipseTemplate = project.task(
                "eclipseTemplate",
                group: "Baseline",
                description: "Update Eclipse settings from stored templates."
            ) << {
                // Copy static files verbatim.
                project.copy {
                    from project.file("${configDir}/eclipse/static")
                    into project.file(".")
                    eachFile { fileDetails ->
                        fileDetails.path = fileDetails.path.replaceAll('dotfile.', '.')
                    }
                    includeEmptyDirs = false  // Skip directories that become empty due to the renaming above.
                }

                // Copy dynamic templates and replace '${variableName}' markers in source files.
                project.copy {
                    from project.file("${configDir}/eclipse/dynamic")
                    into project.file(".")
                    eachFile { fileDetails ->
                        fileDetails.path = fileDetails.path.replaceAll('dotfile.', '.')
                    }
                    includeEmptyDirs = false  // Skip directories that become empty due to the renaming above.
                    expand(configDir: configDir,
                        javaSourceVersion: project.sourceCompatibility,
                        javaTargetVersion: project.targetCompatibility)
                }
            }

            // Run eclipseTemplate when eclipse task is run
            eclipseTemplate.onlyIf {
                project.plugins.hasPlugin(JavaPlugin)
            }
            project.tasks.eclipse.dependsOn(eclipseTemplate)

            // Override default Eclipse JRE.
            if (project.plugins.hasPlugin(JavaPlugin)) {
                project.tasks.eclipseClasspath.doFirst {
                    project.eclipse.classpath {
                        containers.clear()
                        containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-${project.sourceCompatibility}")
                    }
                }
            }
        }
    }
}
