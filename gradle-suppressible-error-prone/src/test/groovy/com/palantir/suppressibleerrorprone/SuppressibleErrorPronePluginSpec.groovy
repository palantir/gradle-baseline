/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.suppressibleerrorprone

import nebula.test.IntegrationSpec

class SuppressibleErrorPronePluginSpec extends IntegrationSpec {
    def setup() {
        // language=Gradle
        buildFile << '''
            apply plugin: 'com.palantir.suppressible-error-prone'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                errorprone 'com.google.errorprone:error_prone_core:2.24.1'
            }
        '''.stripIndent(true)
    }

    def 'does not apply when java plugin is not applied'() {
        // language=Gradle
        buildFile.text = '''
            apply plugin: 'com.palantir.suppressible-error-prone'
            
            task hasErrorPronePlugin {
                doFirst {
                    println "hasErrorPronePlugin: ${pluginManager.hasPlugin('net.ltgt.errorprone')}"
                }
            }
        '''.stripIndent(true)

        when:
        def stdout = runTasksSuccessfully('hasErrorPronePlugin').standardOutput

        then:
        stdout.contains('hasErrorPronePlugin: false')
    }

    def 'errors out when java code that fails an errorprone is applied'() {
        // language=java
        writeJavaSourceFile '''
            package test;
            public final class Test {
                void test() {
                    int[] a = {1, 2, 3};
                    int[] b = {1, 2, 3};
                    System.out.println(a.equals(b));
                }
            }
        '''.stripIndent(true)

        when:
        def stderr = runTasksWithFailure('compileJava').standardError

        then:
        stderr.contains('error: [ArrayEquals]')
    }
}
