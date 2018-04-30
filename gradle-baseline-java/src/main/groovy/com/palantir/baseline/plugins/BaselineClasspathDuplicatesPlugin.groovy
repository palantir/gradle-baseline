/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import java.util.jar.JarEntry
import java.util.jar.JarFile
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.impldep.com.google.common.collect.HashMultimap
import org.gradle.internal.impldep.com.google.common.collect.SetMultimap

class BaselineClasspathDuplicatesPlugin extends AbstractBaselinePlugin  {

    @Override
    void apply(Project project) {
        if (project.plugins.hasPlugin(JavaPlugin)) {
            def silentConflict = project.tasks.create("silentConflict") {
                dependsOn: project.configurations.testRuntime
                doLast {
                    SetMultimap<String, File> classToJarMap = HashMultimap.create()
                    project.configurations.testRuntime.getResolvedConfiguration().getFiles().each { File jarFile ->
                        new JarFile(jarFile).entries()
                                // Only check class uniquness for now
                                .find({JarEntry entry -> entry.getName().endsWith(".class")})
                                .each {JarEntry entry ->
                            classToJarMap.put(entry.getName(), jarFile)
                        }
                    }
                    StringBuilder errors = new StringBuilder();
                    classToJarMap.keys().forEach({key ->
                        Set<File> jars = classToJarMap.get(key)
                        if (jars.size() > 1) {
                            errors.append("Multiple copies of $key discovered in: $jars\n")
                        }
                    })
                    if (errors.length() > 0) {
                        throw new IllegalStateException("Encountered classpath conflicts:\n$errors")
                    }
                }
            }

            project.tasks.check.dependsOn silentConflict
        }
    }
}
