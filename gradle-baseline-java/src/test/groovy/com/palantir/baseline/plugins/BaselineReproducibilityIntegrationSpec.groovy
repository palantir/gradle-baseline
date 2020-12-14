/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class BaselineReproducibilityIntegrationSpec extends IntegrationSpec {

    def 'task surfaces the badness'() {
        when:
        buildFile << """
        ${applyPlugin(BaselineReproducibility.class)}
        apply plugin: 'java'
        apply plugin: 'maven-publish'

        version '1.2.3'

        publishing {
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
        """.stripIndent()

        writeHelloWorld()

        then:
        ExecutionResult output = runTasksWithFailure("check")
        output.getStandardError().contains("./gradlew :checkExplicitSourceCompatibility --fix")
    }

    def 'task passes when explicitly set'() {
        when:
        buildFile << """
        ${applyPlugin(BaselineReproducibility.class)}
        apply plugin: 'java'
        apply plugin: 'maven-publish'

        version '1.2.3'

        sourceCompatibility = 1.8

        publishing {
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
        """.stripIndent()

        writeHelloWorld()

        then:
        def result = runTasksSuccessfully("checkExplicitSourceCompatibility")
    }

    def 'no-op if nothing is published'() {
        when:
        buildFile << """
        ${applyPlugin(BaselineReproducibility.class)}
        apply plugin: 'java'
        version '1.2.3'
        apply plugin: 'maven-publish'
        """.stripIndent()

        writeHelloWorld()

        then:
        def output = runTasksSuccessfully("check")
        output.getStandardOutput().contains("> Task :checkExplicitSourceCompatibility SKIPPED")
    }

    def 'no-op if there is not source'() {
        when:
        buildFile << """
        ${applyPlugin(BaselineReproducibility.class)}
        apply plugin: 'java'
        apply plugin: 'maven-publish'
        version '1.2.3'

        publishing {
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
        """.stripIndent()

        then:
        def output = runTasksSuccessfully("check")
        output.getStandardOutput().contains("> Task :checkExplicitSourceCompatibility SKIPPED")
    }
}
