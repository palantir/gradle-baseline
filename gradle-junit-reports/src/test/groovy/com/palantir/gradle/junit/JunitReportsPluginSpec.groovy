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

package com.palantir.gradle.junit

import nebula.test.IntegrationSpec
import spock.lang.Unroll

@Unroll
class JunitReportsPluginSpec extends IntegrationSpec {
    private static final List<String> GRADLE_TEST_VERSIONS = ['5.6.4', '6.1']

    def '#gradleVersionNumber: applies correctly'() {
        setup:
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'checkstyle'
            apply plugin: 'com.palantir.junit-reports'
            
            repositories {
                jcenter()
            }    
        '''.stripIndent()

        file('src/main/java/foo/FailsCheckstyle.java') << '''
            package foo;            
            public class FailsCheckstyle {
                public void bad_method_name() {}
            }
        '''.stripIndent()

        println runTasksSuccessfully('checkstyleMain').standardOutput

        then:
        true

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }
}
