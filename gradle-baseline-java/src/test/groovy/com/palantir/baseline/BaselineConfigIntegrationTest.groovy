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

import org.gradle.testkit.runner.TaskOutcome

/**
 * This test relies on running ./gradlew :gradle-baseline-java-config:publishToMavenLocal.
 */
class BaselineConfigIntegrationTest extends AbstractPluginTest {
    def projectVersion = "git describe --tags --first-parent --dirty=.dirty --abbrev=7".execute().text.trim()
    def standardBuildFile = """
        plugins {
            id 'com.palantir.baseline-config'
        }
        repositories {
            jcenter()
            mavenLocal()
        }
    """.stripIndent()

    def 'Installs config'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
            // see ./gradle-baseline-java-config/build.gradle
            baseline "com.palantir.baseline:gradle-baseline-java-config:${projectVersion}@zip"
        }
        """.stripIndent()

        then:
        with('--stacktrace', '--info', 'baselineUpdateConfig',
                '-Pcom.palantir.baseline-format.eclipse',
                '-Pcom.palantir.baseline-format.palantir-java-format').build()
        directory('.baseline').list().toList().toSet() == [
                'checkstyle', 'copyright', 'eclipse', 'idea', 'spotless'
        ].toSet()
        directory('project').list().toList().isEmpty()
    }

    def 'Installs scala config if scala is present'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        apply plugin: 'scala'
        apply plugin: 'com.palantir.baseline-scalastyle'
        dependencies {
            // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
            // see ./gradle-baseline-java-config/build.gradle
            baseline "com.palantir.baseline:gradle-baseline-java-config:${projectVersion}@zip"
        }
        """.stripIndent()

        then:
        with('--stacktrace', '--info', 'baselineUpdateConfig').build()
        directory('.baseline').list().toList().toSet() == [
                'checkstyle', 'copyright', 'eclipse', 'idea'
        ].toSet()
        directory('project').list().toList().toSet() == ['scalastyle_config.xml'].toSet()
    }

    def './gradlew baselineUpdateConfig should still work even if no configuration dependency is specified'() {
        when:
        buildFile << standardBuildFile

        // forcing is necessary here because Implementation-Version is only available after publish, not during tests
        buildFile << """
        configurations.baseline {
            resolutionStrategy { force 'com.palantir.baseline:gradle-baseline-java-config:${projectVersion}' }
        }
        """

        then:
        with('--stacktrace', '--info', 'baselineUpdateConfig').build()
        !directory('.baseline').list().toList().isEmpty()
        directory('project').list().toList().isEmpty()
    }

    def 'Fails if too many configuration dependencies are specified'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            baseline "com.palantir.baseline:gradle-baseline-java-config:${projectVersion}@zip"
            baseline "com.google.guava:guava:21.0"
        }
        """.stripIndent()

        then:
        with('--stacktrace', '--info', 'baselineUpdateConfig').buildAndFail().output.contains(
                "Expected to find exactly one config dependency in the 'baseline' configuration, found: [/")
    }

    def './gradlew baselineUpdateConfig should be up to date'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
            // see ./gradle-baseline-java-config/build.gradle
            baseline "com.palantir.baseline:gradle-baseline-java-config:${projectVersion}@zip"
        }
        """.stripIndent()

        then:
        with('--stacktrace', '--info', 'baselineUpdateConfig').build()
        def secondResult = with('baselineUpdateConfig').build()
        assert secondResult.tasks(TaskOutcome.UP_TO_DATE).collect { it.getPath() }.contains(':baselineUpdateConfig')
    }

    def 'started pjf conversion disables checkstyle Indentation module'() {
        file('gradle.properties') << """
            com.palantir.baseline-format.palantir-java-format = started
        """.stripIndent()

        buildFile << standardBuildFile
        buildFile << """
            repositories {
                jcenter()
                mavenLocal()
            }
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:${projectVersion}@zip"
            }
        """.stripIndent()

        when:
        with('baselineUpdateConfig').build()

        then:
        !new File(projectDir, '.baseline/checkstyle/checkstyle.xml').readLines().any {
            it.contains '<module name="Indentation">'
        }
    }
}
