/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

class BaselineCircleCiIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-circleci'
        }
        test {
            environment 'CIRCLE_ARTIFACTS', "${projectDir}/artifacts"
        }
    '''.stripIndent()

    def javaFile = '''
        package test;
        public class Test { void test() {} }
        '''.stripIndent()

    def 'collects html reports'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << javaFile

        then:
        with('test').build()
        directory("artifacts").exists()
    }
}
