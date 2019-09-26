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

class BaselineTestingIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-testing'
            id 'com.palantir.consistent-versions' version '1.9.2'
        }
        
        repositories {
            jcenter()
        }
        
        dependencies {
            testCompile 'junit:junit:4.12'
        }
    '''.stripIndent()

    def junit4Test = '''
        package test;
        
        import org.junit.Test;
        
        public class TestClass4 { 
            @Test
            public void test() {}
        }
        '''.stripIndent()

    def junit5Test = '''
        package test;
        
        import org.junit.jupiter.api.Test;
        
        public class TestClass5 { 
            @Test
            public void test() {}
        }
        '''.stripIndent()

    def 'capable of running both junit4 and junit5 tests'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
            testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.4.2") {
                because 'allows JUnit 3 and JUnit 4 tests to run\'
            }
        }
        '''.stripIndent()
        file('src/test/java/test/TestClass4.java') << junit4Test
        file('src/test/java/test/TestClass5.java') << junit5Test

        then:
        BuildResult result = with('test', '--write-locks').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(projectDir, "build/reports/tests/test/classes/test.TestClass4.html").exists()
        new File(projectDir, "build/reports/tests/test/classes/test.TestClass5.html").exists()
    }

    def 'runs integration tests with junit5'() {
        when:
        buildFile << '''
        plugins {
            id 'org.unbroken-dome.test-sets' version '2.1.1'
        }
        '''.stripIndent()
        buildFile << standardBuildFile
        buildFile << '''

        testSets {
            integrationTest
        }
        
        dependencies {
            integrationTestImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
        }
        '''.stripIndent()
        file('src/integrationTest/java/test/TestClass5.java') << junit5Test

        then:
        BuildResult result = with('integrationTest', '--write-locks').build()
        result.task(':integrationTest').outcome == TaskOutcome.SUCCESS
        new File(projectDir, "build/reports/tests/integrationTest/classes/test.TestClass5.html").exists()
    }

    def 'checkJUnitDependencies ensures mixture of junit4 and 5 tests => legacy must be present'() {
        when:
        buildFile << '''
        plugins {
            id 'org.unbroken-dome.test-sets' version '2.1.1'
        }
        '''.stripIndent()
        buildFile << standardBuildFile
        buildFile << '''
        testSets {
            integrationTest
        }

        dependencies {
            integrationTestImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
        }
        '''.stripIndent()
        file('src/integrationTest/java/test/TestClass2.java') << junit4Test
        file('src/integrationTest/java/test/TestClass5.java') << junit5Test

        then:
        BuildResult result = with('checkJUnitDependencies', '--write-locks').buildAndFail()
        result.output.contains 'Some tests still use JUnit4, but Gradle has been set to use JUnit Platform'
    }

    def 'checkJUnitDependencies ensures mixture of junit4 and 5 tests => new must be present'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testCompile "junit:junit:4.12"
        }
        '''.stripIndent()
        file('src/test/java/test/TestClass2.java') << junit4Test
        file('src/test/java/test/TestClass5.java') << junit5Test

        then:
        BuildResult result = with('checkJUnitDependencies', '--write-locks').buildAndFail()
        result.output.contains 'Some tests mention JUnit5, but the \'test\' task does not have useJUnitPlatform() enabled'
    }
}
