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
import nebula.test.functional.ExecutionResult
import spock.lang.Unroll

@Unroll
class BaselineTestingIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        plugins {
            id 'java-library'
        }
        
        apply plugin: 'com.palantir.baseline-testing'
        
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation 'junit:junit:4.12'
        }
    '''.stripIndent(true)

    def junit4Test = '''
        package test;
        
        import org.junit.Test;
        
        public class TestClass4 { 
            @Test
            public void test() {}
        }
        '''.stripIndent(true)

    def junit5Test = '''
        package test;
        
        import org.junit.jupiter.api.Test;
        
        public class TestClass5 { 
            @Test
            public void test() {}
        }
        '''.stripIndent(true)

    def '#gradleVersionNumber: capable of running both junit4 and junit5 tests'() {
        when:
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
            testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.4.2") {
                because 'allows JUnit 3 and JUnit 4 tests to run\'
            }
        }
        '''.stripIndent(true)
        file('src/test/java/test/TestClass4.java') << junit4Test
        file('src/test/java/test/TestClass5.java') << junit5Test

        then:
        runTasksSuccessfully('test')
        new File(projectDir, "build/reports/tests/test/classes/test.TestClass4.html").exists()
        new File(projectDir, "build/reports/tests/test/classes/test.TestClass5.html").exists()

        where:
        gradleVersionNumber << GradleTestVersions.VERSIONS
    }

    def 'runs integration tests with junit5'() {
        when:
        buildFile << '''
        plugins {
            id 'org.unbroken-dome.test-sets' version '4.0.0'
        }
        '''.stripIndent(true)
        buildFile << standardBuildFile
        buildFile << '''

        testSets {
            integrationTest
        }
        
        dependencies {
            integrationTestImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
        }
        '''.stripIndent(true)
        file('src/integrationTest/java/test/TestClass5.java') << junit5Test

        then:
        runTasksSuccessfully('integrationTest')
        fileExists("build/reports/tests/integrationTest/classes/test.TestClass5.html")
    }
    
    def 'runs nebula-test version 10+ tests that require junit platform'() {
        buildFile << standardBuildFile
        
        buildFile << '''
            apply plugin: 'groovy'
            dependencies {
                testImplementation 'com.netflix.nebula:nebula-test:10.0.0'
            }
        '''.stripIndent(true)

        file('src/test/groovy/test/Test.groovy') << '''
            package test
            class Test extends spock.lang.Specification {
                def test() {}
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully('test')

        then:
        true
    }

    def 'checkJUnitDependencies ensures mixture of junit4 and 5 tests => legacy must be present'() {
        when:
        buildFile << '''
        plugins {
            id 'org.unbroken-dome.test-sets' version '4.0.0'
        }
        '''.stripIndent(true)
        buildFile << standardBuildFile
        buildFile << '''
        testSets {
            integrationTest
        }

        dependencies {
            integrationTestImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
        }
        '''.stripIndent(true)
        file('src/integrationTest/java/test/TestClass2.java') << junit4Test
        file('src/integrationTest/java/test/TestClass5.java') << junit5Test

        then:
        ExecutionResult result = runTasksWithFailure('checkJUnitDependencies')
        result.failure.cause.cause.message.contains 'Some tests still use JUnit4, but Gradle has been set to use JUnit Platform'
    }

    def 'checkJUnitDependencies ensures mixture of junit4 and 5 tests => new must be present'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation "junit:junit:4.12"
        }
        '''.stripIndent(true)
        file('src/test/java/test/TestClass2.java') << junit4Test
        file('src/test/java/test/TestClass5.java') << junit5Test

        then:
        ExecutionResult result = runTasksWithFailure('checkJUnitDependencies')
        result.failure.cause.cause.message.contains 'Some tests mention JUnit5, but the \'test\' task does not have useJUnitPlatform() enabled'
    }

    def 'checkJUnitDependencies ensures nebula test => vintage must be present'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
        apply plugin: 'groovy'
        dependencies {
            testImplementation "org.junit.jupiter:junit-jupiter:5.4.2"
            testImplementation 'com.netflix.nebula:nebula-test:7.3.0'
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksWithFailure('checkJUnitDependencies')
        result.failure.cause.cause.message.contains 'Tests may be silently not running! Spock 1.x dependency detected'
    }

    def 'running -Drecreate=true will re-run tests even if no code changes'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass4.java') << junit4Test

        then:
        def result = runTasksSuccessfully('test')
        result.wasExecuted(':test')

        def result2 = runTasksSuccessfully('test')
        result2.wasUpToDate(':test')

        def result3 = runTasksSuccessfully('test', '-Drecreate=true')
        result3.wasExecuted(':test')

        def result4 = runTasksSuccessfully('test', '-Drecreate=true')
        result4.wasExecuted(':test')
    }

    def 'does not crash with non-utf8 resources'() {
        when:
        buildFile << standardBuildFile
        file('src/test/resources/some-binary').newOutputStream().withCloseable {
            // Invalid unicode sequence identifier
            it.write([0xA0, 0xA1] as byte[])
        }

        then:
        runTasksSuccessfully('checkJUnitDependencies')
    }
}
