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

class BaselineReleaseCompatibilityIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-release-compatibility'
        }

        sourceCompatibility = 1.8

        repositories {
            mavenLocal()
            jcenter()
        }
    '''.stripIndent()

    def useJava9Features = '''
        package test;
        public class Invalid {
            void demo() {
                java.util.Optional.of(1).isEmpty(); // this method was added in Java9
            }
        }
    '''.stripIndent()

    def 'compileJava fails when features from Java9 are used'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Invalid.java').text = useJava9Features

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
    }

    def 'compileJava succeeds when sourceCompatibility = 11 and Java9 features are used'() {
        Assume.assumeTrue(
                "This test can only pass when run from a Java9+ JVM.",
                JavaVersion.current().isJava9Compatible())

        when:
        buildFile << standardBuildFile
        buildFile << 'sourceCompatibility = 11'
        file('src/main/java/test/Invalid.java').text = useJava9Features

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }
}
