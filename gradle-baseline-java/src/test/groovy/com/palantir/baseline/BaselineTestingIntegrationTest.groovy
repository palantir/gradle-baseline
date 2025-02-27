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

import com.palantir.gradle.plugintesting.GradleTestVersions
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.assertj.core.util.Throwables
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
        
        configurations.all {
            resolutionStrategy {
                force 'com.netflix.nebula:nebula-test:10.2.0'
                force 'junit:junit:4.13.2'
                force 'net.jqwik:jqwik:1.9.2'
                force 'org.junit.jupiter:junit-jupiter:5.12.0'
                force 'org.junit.platform:junit-platform-launcher:1.12.0'
                force 'org.junit.vintage:junit-vintage-engine:5.12.0'
            }
        }
    '''.stripIndent(true)

    def junit4Test = '''
        package test;
        
        import org.junit.Test;
        
        public class JUnit4Test { 
            @Test
            public void test() {}
        }
        '''.stripIndent(true)

    def junit5Test = '''
        package test;
        
        import org.junit.jupiter.api.Test;
        
        public class JUnit5Test { 
            @Test
            public void test() {}
        }
        '''.stripIndent(true)

    def jqwikTest = '''
        package test;
        
        import net.jqwik.api.Property;
        import net.jqwik.api.ForAll;
        
        class JqwikTest { 
            @Property
            void test(@ForAll byte value) {}
        }
        '''.stripIndent(true)

    def '#gradleVersionNumber: runs JUnit4 tests'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation 'junit:junit'

            testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
        }
        '''.stripIndent(true)
        file('src/test/java/test/JUnit4Test.java') << junit4Test

        when:
        runTasksSuccessfully('test')

        then:
        fileExists("build/reports/tests/test/classes/test.JUnit4Test.html")

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: runs JUnit5 tests'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        file('src/test/java/test/JUnit5Test.java') << junit5Test

        when:
        runTasksSuccessfully('test')

        then:
        fileExists("build/reports/tests/test/classes/test.JUnit5Test.html")

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: runs both JUnit4 and Junit5 tests'() {
        when:
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation 'junit:junit'

            testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
        }
        '''.stripIndent(true)
        file('src/test/java/test/JUnit4Test.java') << junit4Test
        file('src/test/java/test/JUnit5Test.java') << junit5Test

        then:
        runTasksSuccessfully('test')
        fileExists("build/reports/tests/test/classes/test.JUnit4Test.html")
        fileExists("build/reports/tests/test/classes/test.JUnit5Test.html")

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: runs Jqwik tests'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            testImplementation 'net.jqwik:jqwik'
        }
        '''.stripIndent(true)
        file('src/test/java/test/JqwikTest.java') << jqwikTest

        when:
        runTasksSuccessfully('test')

        then:
        fileExists("build/reports/tests/test/classes/test.JqwikTest.html")

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: runs Nebula tests'() {
        when:
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
            apply plugin: 'groovy'
            dependencies {
                testImplementation 'com.netflix.nebula:nebula-test'
            }
        '''.stripIndent(true)

        file('src/test/groovy/test/Test.groovy') << '''
            package test
            class Test extends spock.lang.Specification {
                def test() {}
            }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('test')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: runs test-sets tests'() {
        when:
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        buildFile << '''
        apply plugin: 'org.unbroken-dome.test-sets'
        testSets {
            integrationTest
        }
        '''.stripIndent(true)
        file('src/integrationTest/java/test/JUnit5Test.java') << junit5Test

        then:
        runTasksSuccessfully('integrationTest')
        fileExists("build/reports/tests/integrationTest/classes/test.JUnit5Test.html")

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: checkJUnitDependencies => JUnit4 without junit-vintage-engine'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        file('src/test/java/test/JUnit4Test.java') << junit4Test

        when:
        String message = Throwables.getRootCause(runTasksWithFailure('checkJUnitDependencies').failure).message

        then:
        message.contains 'Some tests use JUnit4, but the \'test\' task is not using the JUnit Vintage engine.'

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: checkJUnitDependencies => JUnit5 without junit-jupiter'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        // The junit-jupiter dependency is added automatically by the jvm-test-suite plugin, so it is practically
        // impossible for junit-jupiter to be absent. We manually exclude it here in order to test this case.
        buildFile << '''
        configurations {
            testRuntimeClasspath.exclude group: 'org.junit.jupiter', module: 'junit-jupiter'
        }
        '''.stripIndent(true)
        file('src/test/java/test/JUnit5Test.java') << junit5Test

        when:
        String message = Throwables.getRootCause(runTasksWithFailure('checkJUnitDependencies').failure).message

        then:
        message.contains 'Some tests use JUnit5, but the \'test\' task is not using the JUnit Jupiter engine.'

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: checkJUnitDependencies => Jqwik without jqwik-engine'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile
        // jqwik depends on jqwik-engine, so it is practically impossible for jqwik-engine to be absent. We manually exclude it
        // here in order to test this case.
        buildFile << '''
        dependencies { 
            testImplementation 'net.jqwik:jqwik'
        }
        
        configurations {
            testRuntimeClasspath.exclude group: 'net.jqwik', module: 'jqwik-engine'
        }
        '''.stripIndent(true)
        file('src/test/java/test/JqwikTest.java') << jqwikTest

        when:
        String message = Throwables.getRootCause(runTasksWithFailure('checkJUnitDependencies').failure).message

        then:
        message.contains 'Some tests use Jqwik, but the \'test\' task is not using the Jqwik engine.'

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: checkJUnitDependencies => run as part of check'() {
        gradleVersion = gradleVersionNumber

        buildFile << standardBuildFile

        when:
        ExecutionResult result = runTasksSuccessfully('check')

        then:
        result.wasExecuted('checkJUnitDependencies')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def 'running -Drecreate=true will re-run tests even if no code changes'() {
        buildFile << standardBuildFile
        file('src/test/java/test/JUnit5Test.java') << junit5Test

        when:
        def result = runTasksSuccessfully('test')

        then:
        result.wasExecuted(':test')

        when:
        def result2 = runTasksSuccessfully('test')

        then:
        result2.wasUpToDate(':test')

        when:
        def result3 = runTasksSuccessfully('test', '-Drecreate=true')

        then:
        result3.wasExecuted(':test')

        when:
        def result4 = runTasksSuccessfully('test', '-Drecreate=true')

        then:
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
