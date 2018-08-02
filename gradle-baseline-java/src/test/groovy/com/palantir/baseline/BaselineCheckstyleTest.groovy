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

package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineCheckstyle
import com.palantir.baseline.plugins.BaselineEclipse
import nebula.test.multiproject.MultiProjectIntegrationHelper
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BaselineCheckstyleTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineCheckstyle
    }

    def baselineCheckstylePluginApplied() {
        expect:
        project.plugins.hasPlugin(BaselineCheckstyle.class)
    }

    def checkstylePluginApplied() {
        expect:
        project.plugins.hasPlugin(CheckstylePlugin.class)
    }

    def includesResources() {
        def file = new File(project.projectDir, 'src/test/resources/checkstyle.xml')
        file.getParentFile().mkdirs()
        when:
        file << '''
        <?xml version="1.0"?>
        '''.stripIndent()

        then:
        def tasks = project.tasks.withType(Checkstyle.class)
        for (Checkstyle task : tasks) {
            assert task.getSource().getFiles().contains(file)
        }
    }

    def appliesEclipseNatures() {
        when:
        project.plugins.apply 'eclipse'
        project.plugins.apply BaselineEclipse

        then:
        project.eclipse.project.natures.contains("net.sf.eclipsecs.core.CheckstyleNature")
    }
}
