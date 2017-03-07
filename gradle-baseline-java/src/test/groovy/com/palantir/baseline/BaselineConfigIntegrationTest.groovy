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

class BaselineConfigIntegrationTest extends AbstractPluginTest {
    def projectVersion = "git describe --tags".execute().text.trim()
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
        with('baselineUpdateConfig').build()
        directory('.baseline').list().toList() == ['checkstyle', 'copyright', 'eclipse', 'findbugs', 'idea']
    }

    def 'Fails if no configuration dependency is specified'() {
        when:
        buildFile << standardBuildFile

        then:
        with('baselineUpdateConfig').buildAndFail().output.contains(
                "Expected to find exactly one config dependency in the 'baseline' configuration, found: []")
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
        with('baselineUpdateConfig').buildAndFail().output.contains(
                "Expected to find exactly one config dependency in the 'baseline' configuration, found: [/")
    }
}
