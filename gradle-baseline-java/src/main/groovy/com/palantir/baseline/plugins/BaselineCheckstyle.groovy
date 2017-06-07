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
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Configures the Gradle 'checkstyle' task with Baseline settings.
 */
class BaselineCheckstyle extends AbstractBaselinePlugin {

    static String DEFAULT_CHECKSTYLE_VERSION = '7.8'

    void apply(Project project) {
        this.project = project

        project.plugins.apply CheckstylePlugin

        // Set default version (outside afterEvaluate so it can be overridden).
        project.extensions.findByType(CheckstyleExtension).toolVersion = DEFAULT_CHECKSTYLE_VERSION

        project.afterEvaluate { Project p ->
            configureCheckstyle()
            configureCheckstyleForEclipse()

            // We use the "JavadocMethod" module in our Checkstyle configuration, making
            // Java 8+ new doclint compiler feature redundant.
            project.tasks.withType(Javadoc) { it ->
                if (project.sourceCompatibility.isJava8Compatible()) {
                    it.options.addStringOption('Xdoclint:none', '-quiet')
                }
            }
        }
    }

    def configureCheckstyle() {
        project.logger.debug("Baseline: Configuring Checkstyle tasks")

        def configProps = project.checkstyle.configProperties
        // Required to enable checkstyle suppressions
        configProps['samedir'] = "${configDir}/checkstyle"

        // Configure checkstyle
        project.checkstyle {
            configFile = project.file("${configDir}/checkstyle/checkstyle.xml")
            configProperties = configProps
        }

        // Set custom source rules for checkstyleMain task.
        Checkstyle task = (Checkstyle) project.tasks.checkstyleMain

        // Make checkstyle include files in src/main/resources and src/test/resources, e.g., for whitespace checks.
        task.source 'src/main/resources'
        task.source 'src/test/resources'

        // These sources are only checked by gradle, NOT by Eclipse.
        def sources = ['checks', 'manifests', 'scripts', 'templates']
        sources.each { source -> task.source source }

        // Make sure java files are still included. This should match list in etc/eclipse-template/.checkstyle.
        // Currently not enforced, but could be eventually.
        def includeExtensions =
                ['java', 'cfg', 'coffee', 'erb', 'groovy', 'handlebars', 'json', 'less', 'pl', 'pp', 'sh', 'xml']
        includeExtensions.each { extension ->
            task.include "**/*.$extension"
        }

        // Work around https://github.com/gradle/gradle/issues/855
        project.tasks.checkstyleMain.classpath += project.configurations.compileClasspath
        project.tasks.checkstyleTest.classpath += project.configurations.testCompileClasspath
    }

    def configureCheckstyleForEclipse() {
        def eclipse = project.plugins.findPlugin "eclipse"
        if (eclipse == null) {
            project.logger.debug "Baseline: Skipping configuring Eclipse for Checkstyle (eclipse not applied)"
            return
        }
        project.logger.debug "Baseline: Configuring Eclipse Checkstyle"
        project.eclipse.project {
            natures "net.sf.eclipsecs.core.CheckstyleNature"
            buildCommand "net.sf.eclipsecs.core.CheckstyleBuilder"
        }
    }
}
