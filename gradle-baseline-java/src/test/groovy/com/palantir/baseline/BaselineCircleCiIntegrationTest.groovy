/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

class BaselineCircleCiIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java-library'
            id 'com.palantir.baseline-circleci'
        }
        
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation 'junit:junit:4.12'
        }
    '''.stripIndent()

    def javaFile = '''
        package test;
        
        import org.junit.Test;
        
        public class TestClass { 
            @Test
            public void test() {} 
        }
        '''.stripIndent()

    def setup() {
        new File(System.getenv('CIRCLE_ARTIFACTS')).toPath().deleteDir()
    }

    def 'collects junit reports'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass.java') << javaFile

        String testReports = System.getenv('CIRCLE_TEST_REPORTS')
        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(new File(testReports, 'junit'), 'test').list().toList().toSet() == ['TEST-test.TestClass.xml'].toSet()
    }

    def 'collects html reports'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass.java') << javaFile

        String artifacts = System.getenv('CIRCLE_ARTIFACTS')
        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(new File(artifacts, 'junit'), 'test').list().toList().toSet() == ['classes', 'css', 'index.html', 'js', 'packages'].toSet()
    }
}
