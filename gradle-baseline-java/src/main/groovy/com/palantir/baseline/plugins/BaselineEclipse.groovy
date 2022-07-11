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

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin

/**
 * Configures the Gradle 'eclipse' task with Baseline settings.
 */
class BaselineEclipse extends AbstractBaselinePlugin {

    /**
     * Copies all name/value pairs from {@code from} to {@code into}, replacing variable names (e.g., $var or ${var}) occurring in values using basic string replacement
     */
    static def mergeProperties(Properties from, Properties into, Map binding) {
        from.stringPropertyNames().each { property ->
            try {
                def propertyValue = replace(from.getProperty(property), binding)
                into.setProperty(property, propertyValue)
            }
            catch(Exception e) {
                e.printStackTrace()
            }
        }
    }

    static String replace(String str, Map binding) {
        StringBuilder sb = new StringBuilder()
        int start = 0

        int begin = str.indexOf("\${", start)
        if (begin < 0) {
            sb.append(str.substring(start))
            return sb.toString()
        }

        int end = str.indexOf('}', begin)
        if (end < 0) {
            throw new Exception("Missing '}' for property: " + str)
        }

        String replacement = binding.get(str.substring(begin + 2, end))
        if (replacement != null) {
            sb.append(str.substring(start, begin))
            sb.append(replacement)
        } else {
            throw new Exception("Replacement missing from binding for property: " + str)
        }
        return sb.toString()
    }

    void apply(Project project) {
        this.project = project

        // Configure Eclipse JDT Core by merging in Baseline settings.
        project.plugins.withType(JavaPlugin, { plugin ->
            project.plugins.apply EclipsePlugin
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

                // Configure Checkstyle/JdtUI settings by copying in the default Baseline config file.
                // Warning: this may interfere with other Gradle plugins that may try to mutate these files.
                def eclipseTemplate = project.task(
                    "eclipseTemplate",
                    group: "Baseline",
                    description: "Update Eclipse settings from stored templates."
                ).doLast {
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
                project.tasks.named("eclipse").configure {
                    dependsOn(eclipseTemplate)
                }

                // Override default Eclipse JRE.
                project.tasks.named("eclipseClasspath").configure {
                    doFirst {
                        String eclipseClassPath = "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + project.sourceCompatibility;
                        project.eclipse.classpath {
                            containers.clear()
                            containers.add(eclipseClassPath)
                        }
                    }
                }
            }
        })
    }
}
