/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.junit


import nebula.test.IntegrationSpec

class JunitReportsPluginIntegrationSpec extends IntegrationSpec {


    def setup() {
        buildFile << """
        ${applyPlugin(JunitReportsRootPlugin)}
        
        task foo {
            doLast {
                throw new RuntimeException("Failure")
            }
        }
        """.stripIndent()
    }

    def 'captures specific failures for registered tasks'() {
        when:
        buildFile << """
        import com.palantir.gradle.junit.Failure

        junitTaskResults {
            registerTask 'foo', project.provider({ -> [
                new Failure.Builder()
                    .severity("error")
                    .file(file('build.gradle'))
                    .line(1)
                    .message("some failure")
                    .build()
            ]})
        }
        """.stripIndent()

        then:
        def result = runTasksWithFailure('foo')
        result.wasExecuted('fooJunitReportsFinalizer')
        fileExists("build/junit-reports/${moduleName}-foo.xml")
    }

    def 'captures failure for non-registered tasks'() {
        expect:
        def result = runTasksWithFailure('foo')
        !result.wasExecuted('fooJunitReportsFinalizer')
        !fileExists("build/junit-reports/${moduleName}-foo.xml")
        file('build/junit-reports/gradle/build.xml').text.contains "<failure message=\"RuntimeException: Failure\" type=\"ERROR\">"
    }
}
