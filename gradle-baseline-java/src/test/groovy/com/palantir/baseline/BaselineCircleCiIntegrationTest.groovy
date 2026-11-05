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


import java.nio.file.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineCircleCiIntegrationTest extends AbstractPluginTest {
    private File artifactsDir
    private File testReportsDir

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

    def failingJavaFile = '''
        package test;

        import org.junit.Test;

        public class TestClass {
            @Test
            public void test() {
                throw new AssertionError();
            }
        }
        '''.stripIndent()

    def setup() {
        artifactsDir = new File(projectDir, 'circle/artifacts')
        testReportsDir = new File(projectDir, 'circle/test-reports')
        file('gradle.properties') << """
            __TESTING=true
            __TESTING_CIRCLE_ARTIFACTS=${artifactsDir}
            __TESTING_CIRCLE_TEST_REPORTS=${testReportsDir}
        """.stripIndent()
    }

    def 'collects junit reports'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass.java') << javaFile

        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(testReportsDir, 'junit/test').list().toList().toSet() == ['TEST-test.TestClass.xml'].toSet()
    }

    def 'collects html reports'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass.java') << javaFile

        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(artifactsDir, 'junit/test/index.html').isFile()
    }

    def 'html-test-reports-failed-only removes html reports of passing test tasks'() {
        when:
        buildFile << standardBuildFile
        file('gradle.properties') << 'com.palantir.baseline-circleci.html-test-reports-failed-only=true\n'
        file('src/test/java/test/TestClass.java') << javaFile

        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        !containsHtmlReport(new File(artifactsDir, 'junit/test'))
    }

    def 'html-test-reports-failed-only keeps html reports of failing test tasks'() {
        when:
        buildFile << standardBuildFile
        file('gradle.properties') << 'com.palantir.baseline-circleci.html-test-reports-failed-only=true\n'
        file('src/test/java/test/TestClass.java') << failingJavaFile

        then:
        BuildResult result = with('test').buildAndFail()
        result.task(':test').outcome == TaskOutcome.FAILED
        new File(artifactsDir, 'junit/test/index.html').isFile()
    }

    private static boolean containsHtmlReport(File directory) {
        return Files.walk(directory.toPath()).withCloseable { paths ->
            paths.anyMatch { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith('.html') }
        }
    }
}
