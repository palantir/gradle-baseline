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
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.apache.commons.io.FileUtils

class BaselineIdeaIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'com.palantir.baseline-idea'
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
        runTasksSuccessfully('idea')
    }

    def 'Throws error when configuration files are not present'() {
        when:
        buildFile << standardBuildFile
        FileUtils.deleteDirectory(new File(projectDir, ".baseline"))

        then:
        ExecutionResult result = runTasksWithFailure('idea')
        assert result.standardError.contains("Caused by: java.io.FileNotFoundException:")
    }

    def 'Can apply plugin to subprojects when root project has no idea plugin applied'() {
        when:
        buildFile << '''
            apply plugin: 'java'
        '''.stripIndent()
        def subproject = helper.create(["subproject"])["subproject"]
        subproject.buildGradle << '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-idea'
        '''.stripIndent()

        then:
        runTasksSuccessfully('idea')
    }

    def 'Modules for subprojects pick up the correct sourceCompatibility'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceCompatibility = 1.6
        '''.stripIndent()
        def subproject = helper.create(["subproject1", "subproject2"])
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
        runTasksSuccessfully('idea')
        def rootIml = Files.asCharSource(new File(projectDir,
                "Modules-for-subprojects-pick-up-the-correct-sourceCompatibility.iml"), Charsets.UTF_8).read()
        assert rootIml.contains('<orderEntry type="jdk" jdkName="1.6" jdkType="JavaSDK"/>')
        def subproject1Iml = Files.asCharSource(new File(projectDir, "subproject1/subproject1.iml"), Charsets.UTF_8).read()
        assert subproject1Iml.contains('<orderEntry type="jdk" jdkName="1.7" jdkType="JavaSDK"/>')
        def subproject2Iml = Files.asCharSource(new File(projectDir, "subproject2/subproject2.iml"), Charsets.UTF_8).read()
        assert subproject2Iml.contains('<orderEntry type="jdk" jdkName="1.8" jdkType="JavaSDK"/>')
    }

    def 'Idea project has copyright configuration'() {
        when:
        buildFile << standardBuildFile

        then:
        runTasksSuccessfully('idea')
        def rootIpr = Files.asCharSource(new File(projectDir,
                "Idea-project-has-copyright-configuration.ipr"), Charsets.UTF_8).read()
        assert rootIpr.contains('<option name="myName" value="apache-2.0.txt"/>')
        assert rootIpr.contains('<component name="CopyrightManager" default="apache-2.0.txt">')
    }

    def 'Git support is added if .git directory is present'() {
        when:
        buildFile << standardBuildFile
        new File(projectDir, ".git").mkdir()

        then:
        runTasksSuccessfully('idea')
        def rootIpr = Files.asCharSource(new File(projectDir,
                "Git-support-is-added-if-git-directory-is-present.ipr"), Charsets.UTF_8).read()
        assert rootIpr.contains('<mapping directory="$PROJECT_DIR$" vcs="Git"/>')
    }

    def 'Git support is not added if .git directory is not present'() {
        when:
        buildFile << standardBuildFile

        then:
        runTasksSuccessfully('idea')
        def rootIpr = Files.asCharSource(new File(projectDir,
                "Git-support-is-not-added-if-git-directory-is-not-present.ipr"), Charsets.UTF_8).read()
        assert !rootIpr.contains('<mapping directory="$PROJECT_DIR$" vcs="Git"/>')
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
        runTasksSuccessfully('idea')
        def iml = Files.asCharSource(new File(projectDir,
                  "Adds-compileOnly-dependencies-if-the-configuration-exists.iml"), Charsets.UTF_8).read()
        assert iml.contains('<orderEntry type="module-library" scope="PROVIDED">')
    }
}
