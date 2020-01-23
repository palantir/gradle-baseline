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

package com.palantir.baseline


import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class JunitReportsPluginSpec extends Specification {
    private static final List<String> GRADLE_TEST_VERSIONS = ['5.6.4', '6.1']

    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def '#gradleVersion: configures the checkstlye plugin correctly'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.baseline-circleci'
            }

            apply plugin: 'java'
            apply plugin: 'checkstyle'
            //  apply plugin: 'com.palantir.baseline-circleci'
            
            repositories {
                jcenter()
            }
        '''.stripIndent()

        def javaCode = new File(testProjectDir.getRoot(), 'src/main/java/foo/Foo.java')
        javaCode.getParentFile().mkdirs()
        javaCode << '''
            package foo;
            public class Foo {}
        '''.stripIndent()

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('-s', 'checkstyleMain')
                .withDebug(false)
                .build()

        then:
        result.task('checkstyleMain').outcome == TaskOutcome.SUCCESS

        where:
        gradleVersion << GRADLE_TEST_VERSIONS
    }
}
