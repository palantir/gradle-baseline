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
        allprojects {
            apply plugin: 'com.palantir.baseline-idea'
            repositories {
                maven { url 'https://dl.bintray.com/palantir/releases' }
                jcenter()
            }
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

    def 'scalastyle renders correctly'() {
        when:
        buildFile << '''
        plugins {
            id 'scala'
            id 'com.palantir.baseline-idea'
            id 'com.palantir.baseline-scalastyle'
        }
        repositories {
            jcenter()
            mavenLocal()
        }
        dependencies {
            compile 'org.scala-lang:scala-library:2.11.12'
        }
        '''.stripIndent()

        then:
        BuildResult result = with('--stacktrace', '--info', 'idea').build()
        assert result.tasks(TaskOutcome.SUCCESS).collect { it.path }.contains(':idea')
    }

    def 'deletes redundant iml,ipr,iws files'() {
        when:
        buildFile << standardBuildFile
        File real = new File(projectDir, projectDir.name + ".ipr")
        File iws = createFile('foo.ipr')
        File iml = createFile('foo.iml')
        File ipr = createFile('foo.iws')

        then:
        !real.exists()
        iws.exists() && iml.exists() && ipr.exists()

        with('idea').build()

        real.exists()
        !iws.exists() && !iml.exists() && !ipr.exists()
    }

    def 'does not delete subproject iml has different name than subproject'() {
        def rootProjectName = projectDir.name

        buildFile << standardBuildFile
        File real = new File(projectDir, rootProjectName + ".ipr")
        File iws = createFile('foo.ipr')
        File iml = createFile('foo.iml')
        File ipr = createFile('foo.iws')

        def subprojectDir = multiProject.addSubproject('bar', '''
            idea {
                module {
                    name = 'something-else'
                }
            }
        '''.stripIndent())
        def subprojectIml = new File(subprojectDir, 'something-else.iml')

        expect:
        !real.exists()
        iws.exists() && iml.exists() && ipr.exists()

        when:
        with('idea').build()

        then:
        real.exists()
        subprojectIml.exists() // expect 'idea' to have created it

        when:
        def otherSubprojectIml = createFile('some-other-name.iml', subprojectDir)
        with('idea').build()

        then:
        !otherSubprojectIml.exists()
    }

    def "idea configures the save-action plugin when PJF is enabled on a subproject"() {
        buildFile << standardBuildFile
        multiProject.addSubproject('formatted-project', """
            apply plugin: 'com.palantir.java-format'
        """.stripIndent())

        when:
        with('idea').build()

        then:
        def iprFile = new File(projectDir, "${moduleName}.ipr")
        def ipr = new XmlSlurper().parse(iprFile)
        ipr.component.find { it.@name == "ExternalDependencies" }
        ipr.component.find { it.@name == "SaveActionSettings" }
    }

    def "idea does not configure the save-action plugin when PJF is not enabled"() {
        buildFile << standardBuildFile

        when:
        with('idea').build()

        then:
        def iprFile = new File(projectDir, "${moduleName}.ipr")
        def ipr = new XmlSlurper().parse(iprFile)
        !ipr.component.find { it.@name == "ExternalDependencies" }
        !ipr.component.find { it.@name == "SaveActionSettings" }
    }
}
