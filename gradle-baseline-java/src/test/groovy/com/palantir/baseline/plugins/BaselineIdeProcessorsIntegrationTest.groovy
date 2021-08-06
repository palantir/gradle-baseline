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

import com.palantir.baseline.AbstractPluginTest
import groovy.util.slurpersupport.NodeChildren
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.util.environment.RestoreSystemProperties

class BaselineIdeProcessorsIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'idea'
            id 'com.palantir.baseline-ide-processors'
        }
        allprojects {
            apply plugin: 'java'
            apply plugin: 'idea'
            apply plugin: 'com.palantir.baseline-ide-processors'
            repositories {
                mavenCentral()
                jcenter()
            }
        }
    '''.stripIndent()

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result = with('idea').build()
        assert result.task(':idea').outcome == TaskOutcome.SUCCESS ?: result.output
    }

    def 'Can apply plugin to subprojects when root project has no idea plugin applied'() {
        when:
        buildFile << '''
            apply plugin: 'java'
        '''.stripIndent()
        def subproject = multiProject.create(["subproject"])["subproject"]
        subproject.buildGradle << standardBuildFile

        then:
        with('idea').build()
    }

    @RestoreSystemProperties
    def 'Do not modify idea iml if importing'() {
        when:
        buildFile << standardBuildFile
        System.setProperty("idea.active", "true")

        then:
        with('idea').build()
        NodeChildren sourceFolders = imlSourceFolders()
        doesNotContainNodeWithAttributes(sourceFolders, "file://\$MODULE_DIR\$/generated_testSrc", true, true)
        doesNotContainNodeWithAttributes(sourceFolders, "file://\$MODULE_DIR\$/generated_src", false, true)
    }

    def 'sourceFolders are added to project iml'() {
        when:
        buildFile << standardBuildFile

        then:
        with('idea').build()
        NodeChildren sourceFolders = imlSourceFolders()
        containsNodeWithAttributes(sourceFolders, "file://\$MODULE_DIR\$/generated_testSrc", true, true)
        containsNodeWithAttributes(sourceFolders, "file://\$MODULE_DIR\$/generated_src", false, true)
    }

    private NodeChildren imlSourceFolders() {
        def xml = new XmlSlurper().parse(new File(projectDir, projectDir.name + ".iml"));
        def component = xml.component.findResult { it.@name == "NewModuleRootManager" ? it : null }
        return component.content[0].sourceFolder
    }

    private boolean containsNodeWithAttributes(NodeChildren children, String url, boolean isTestSource, boolean generated) {
        return shouldContainNodeWithAttributes(true, children, url, isTestSource, generated)
    }

    private boolean doesNotContainNodeWithAttributes(NodeChildren children, String url, boolean isTestSource, boolean generated) {
        return shouldContainNodeWithAttributes(false, children, url, isTestSource, generated)
    }

    private boolean shouldContainNodeWithAttributes(boolean shouldContain, NodeChildren children, String url, boolean isTestSource, boolean generated) {
        def matches = children.findAll { it.@url == url && it.@isTestSource == isTestSource && it.@generated == generated }
        int expectedSize = shouldContain ? 1 : 0;
        return matches.size() == expectedSize;
    }
}
