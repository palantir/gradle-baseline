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

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume
import spock.lang.Unroll

@Unroll
class BaselineReleaseCompatibilityIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-release-compatibility'
        }

        sourceCompatibility = 11

        repositories {
            mavenLocal()
            jcenter()
        }
    '''.stripIndent()

    def useJava15Features = '''
        package test;
        public class Invalid {
            void demo() {
                // Added in Java 15
                String example = """
                    text block""";
            }
        }
    '''.stripIndent()

    def 'compileJava fails when features from Java 15 are used'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Invalid.java').text = useJava15Features

        then:
        BuildResult result = with('compileJava').withGradleVersion(gradleVersion).buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED

        where:
        gradleVersion << GradleTestVersions.VERSIONS
    }

    def 'compileJava succeeds when sourceCompatibility = 15 and Java 15 features are used'() {
        Assume.assumeTrue(
                "This test can only pass when run from a Java9+ JVM.",
                JavaVersion.current() >= JavaVersion.VERSION_15)

        when:
        buildFile << standardBuildFile
        buildFile << 'sourceCompatibility = 15'
        file('src/main/java/test/Invalid.java').text = useJava15Features

        then:
        BuildResult result = with('compileJava').withGradleVersion(gradleVersion).build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        where:
        gradleVersion << GradleTestVersions.VERSIONS
    }
}
