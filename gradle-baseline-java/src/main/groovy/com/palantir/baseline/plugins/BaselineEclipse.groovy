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

import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin

/**
 * Configures the Gradle 'eclipse' task with Baseline settings.
 */
class BaselineEclipse extends AbstractBaselinePlugin {

    /**
     * Copies all name/value pairs from {@code from} to {@code into}, replacing variable names (e.g., $var or ${var})
     * occurring in values via a {@link SimpleTemplateEngine} and the given binding.
     */
    static def mergeProperties(Properties from, Properties into, Map binding) {
        from.stringPropertyNames().each { property ->
            def propertyValue = from.getProperty(property)
            try {
                // Not all properties are well-formed; attempt to parse and fall-back to original value.
                // Sadly, this is outrageously slow.
                def template = new SimpleTemplateEngine().createTemplate(from.getProperty(property))
                propertyValue = template.make(binding).toString()
            } catch (Exception e) {
                propertyValue = from.getProperty(property)
            }

            into.setProperty(property, propertyValue)
        }
    }

    void apply(Project project) {
        this.project = project

        project.plugins.apply EclipsePlugin

        // Configure Eclipse JDT Core by merging in Baseline settings.
        project.plugins.withType(EclipsePlugin, { plugin ->
            project.afterEvaluate {
                project.eclipse {
                    if (jdt != null) {
                        // Read baseline configuration from config directory
                        def baselineJdtCoreProps = new Properties()
                        def baselineJdtCorePropsFile = project.file("${configDir}/eclipse/org.eclipse.jdt.core.prefs")
                        if (baselineJdtCorePropsFile.canRead()) {
                            def reader = baselineJdtCorePropsFile.newReader()
                            baselineJdtCoreProps.load(reader)
                            reader.close()

                            def binding = [
                                javaSourceVersion: project.sourceCompatibility,
                                javaTargetVersion: project.targetCompatibility]

                            // Merge baseline config into default config
                            jdt.file.withProperties { Properties baseProperties ->
                                mergeProperties(baselineJdtCoreProps, baseProperties, binding)
                            }
                        } else {
                            project.logger.error("Cannot read Baseline Eclipse configuration, not configuring Eclipse: {}",
                                baselineJdtCorePropsFile)
                        }
                    }
                }
            }
        })

        // Configure Findbugs/Checkstyle/JdtUI settings by copying in the default Baseline config file.
        // Warning: this may interfere with other Gradle plugins that may try to mutate these files.
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
                    expand(configDir: configDir)
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
