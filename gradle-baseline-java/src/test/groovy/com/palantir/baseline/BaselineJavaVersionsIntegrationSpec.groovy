/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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


import nebula.test.IntegrationSpec

class BaselineJavaVersionsIntegrationSpec extends IntegrationSpec {
    def test() {
        // language=Gradle
        buildFile << '''
            apply plugin: 'com.palantir.baseline-java-versions'

            allprojects {
                apply plugin: 'java'
            }
        '''.stripIndent(true)

        addSubproject 'foo'

        // language=Gradle
        addSubproject 'bar', '''
            dependencies {
                implementation project(':foo')
            }
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('compileJava').standardOutput

        then:
        stdout.contains('lol')
    }
}
