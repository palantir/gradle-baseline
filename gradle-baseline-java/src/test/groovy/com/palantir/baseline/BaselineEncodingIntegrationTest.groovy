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


import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineEncodingIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-encoding'
        }

        sourceCompatibility = 1.8

        repositories {
            mavenLocal()
            jcenter()
        }
    '''.stripIndent()

    def otherEncodingBuildFile = '''
        plugins {
            id 'java'
        }

        sourceCompatibility = 1.8

        repositories {
            mavenLocal()
            jcenter()
        }
        
        tasks.withType(JavaCompile) {
            options.encoding = 'US-ASCII'
        }
    '''.stripIndent()

    def javaFile = '''
        package test;
        public class Test {
            private static final String VALUE = "•";
        }
    '''.stripIndent()

    def 'compileJava succeeds with baseline-encoding'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java').text = javaFile

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        !result.output.contains("unmappable character")
    }

    def 'compileJava fails with other encoding'() {
        when:
        buildFile << otherEncodingBuildFile
        file('src/main/java/test/Test.java').text = javaFile

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        result.output.contains("unmappable character")
    }
}
