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

import com.google.common.base.Charsets
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineIdeaIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-idea'
        }
    '''.stripIndent()

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('idea').build()
    }

    def 'Throws error when configuration files are not present'() {
        when:
        buildFile << standardBuildFile
        FileUtils.deleteDirectory(new File(projectDir, ".baseline"))

        then:
        BuildResult result = with('idea').buildAndFail()
        result.task(":ideaProject").outcome == TaskOutcome.FAILED
        result.output.contains("java.io.FileNotFoundException:")
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

    def 'Modules for subprojects pick up the correct sourceCompatibility'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceCompatibility = 1.6
        '''.stripIndent()
        def subproject = multiProject.create(["subproject1", "subproject2"])
        subproject["subproject1"].buildGradle << '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-idea'
            sourceCompatibility = 1.7
        '''.stripIndent()
        subproject["subproject2"].buildGradle << '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-idea'
            sourceCompatibility = 1.8
        '''.stripIndent()

        then:
        with('idea').build()
        def rootIml = Files.asCharSource(new File(projectDir, projectDir.name + ".iml"), Charsets.UTF_8).read()
        rootIml ==~ /(?s).*orderEntry[^\\n]*jdkName="1.6".*/
        def subproject1Iml = Files.asCharSource(new File(projectDir, "subproject1/subproject1.iml"),
                Charsets.UTF_8).read()
        subproject1Iml ==~ /(?s).*orderEntry[^\\n]*jdkName="1.7".*/
        def subproject2Iml = Files.asCharSource(new File(projectDir, "subproject2/subproject2.iml"),
                Charsets.UTF_8).read()
        subproject2Iml ==~ /(?s).*orderEntry[^\\n]*jdkName="1.8".*/
    }

    def 'Idea project has copyright configuration'() {
        when:
        buildFile << standardBuildFile

        then:
        with('idea').build()
        def rootIpr = Files.asCharSource(new File(projectDir, projectDir.name + ".ipr"), Charsets.UTF_8).read()
        rootIpr.contains('<option name="myName" value="001_apache-2.0.txt"/>')
        rootIpr.contains('<option name="myName" value="999_palantir.txt"/>')
        rootIpr.contains('<component name="CopyrightManager" default="999_palantir.txt">')
    }

    def 'Git support is added if .git directory is present'() {
        when:
        buildFile << standardBuildFile
        new File(projectDir, ".git").mkdir()

        then:
        with('idea').build()
        def rootIpr = Files.asCharSource(new File(projectDir, projectDir.name + ".ipr"), Charsets.UTF_8).read()
        rootIpr.contains('<mapping directory="$PROJECT_DIR$" vcs="Git"/>')
    }

    def 'Git support is not added if .git directory is not present'() {
        when:
        buildFile << standardBuildFile

        then:
        with('idea').build()
        def rootIpr = Files.asCharSource(new File(projectDir, projectDir.name + ".ipr"), Charsets.UTF_8).read()
        !rootIpr.contains('<mapping directory="$PROJECT_DIR$" vcs="Git"/>')
    }

    def 'Adds compileOnly dependencies if the configuration exists'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            configurations {
                compileOnly
            }

            dependencies {
                compileOnly localGroovy()
            }
        """

        then:
        with('idea').build()
        def iml = Files.asCharSource(new File(projectDir, projectDir.name + ".iml"), Charsets.UTF_8).read()
        iml ==~ /(?s).*orderEntry[^\\n]*scope="PROVIDED".*/
    }

    def 'Doesn\'t make compile dependencies provided unnecessarily'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            dependencies {
                compile localGroovy()
            }
        """

        then:
        with('idea').build()
        def iml = Files.asCharSource(new File(projectDir, projectDir.name + ".iml"), Charsets.UTF_8).read()
        iml ==~ /(?s).*orderEntry[^\\n]*type="module-library".*/
    }

    def 'Modifies launch configuration defaults'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            dependencies {
                compile localGroovy()
            }
            idea.workspace {
                iws.withXml { provider ->
                    def runManager = provider.node.component.find {it.@name == 'RunManager'}
                    println runManager
                    def appJunitDefaults = new NodeList(runManager.configuration
                            .findAll { it.'@default' == 'true' && it.'@type' in ['Application', 'JUnit'] })
                    assert appJunitDefaults.size() == 2
                    def workingDirectories = new NodeList(appJunitDefaults.option
                            .findAll { it.'@name' == 'WORKING_DIRECTORY' })
                    assert workingDirectories.size() == 2
                    workingDirectories.each { it.'@value' = 'file://abc' }
                }
            }
        '''

        then:
        with('idea').build()
        def iws = Files.asCharSource(new File(projectDir, projectDir.name + ".iws"), Charsets.UTF_8).read()
        iws ==~ '(?s).*name="WORKING_DIRECTORY"[^\\n]*value="file://\\$MODULE_DIR\\$".*'
        !(iws ==~ '(?s).*name="WORKING_DIRECTORY"[^\\n]*value="file://abc".*')
    }

    def 'Does not modify existing launch configurations'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            dependencies {
                compile localGroovy()
            }
            idea.workspace {
                iws.withXml { provider ->
                    def runManager = provider.node.component.find {it.@name == 'RunManager'}
                    runManager.appendNode('configuration', [default: "false", name: "Test", type: "Application", factoryName: "Application"], [
                            new Node(null, 'option', [name: 'WORKING_DIRECTORY', value: 'file://abc'])])
                }
            }
        '''

        then:
        with('idea').build()
        def iws = Files.asCharSource(new File(projectDir, projectDir.name + ".iws"), Charsets.UTF_8).read()
        iws ==~ '(?s).*name="WORKING_DIRECTORY"[^\\n]*value="file://abc".*'
    }
}
