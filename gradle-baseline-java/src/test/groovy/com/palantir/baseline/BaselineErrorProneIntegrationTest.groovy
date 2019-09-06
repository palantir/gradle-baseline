/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

/**
 * This test depends on ./gradlew :baseline-error-prone:publishToMavenLocal
 */
class BaselineErrorProneIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-error-prone'
            id 'org.inferred.processors' version '1.3.0'
        }
        repositories {
            mavenLocal()
            jcenter()
        }
    '''.stripIndent()

    def validJavaFile = '''
        package test;
        public class Test { void test() {} }
        '''.stripIndent()

    def invalidJavaFile = '''
        package test;
        import java.util.Optional;
        public class Test {
            void test() {
                int[] a = {1, 2, 3};
                int[] b = {1, 2, 3};
                if (a.equals(b)) {
                  System.out.println("arrays are equal!");
                  Optional.of("hello").orElse(System.getProperty("world"));
                }
            }
        }
        '''.stripIndent()

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('compileJava', '--info').build()
    }

    def 'compileJava fails when there is an unclosed stream of files'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            void test() throws java.io.IOException {
                java.nio.file.Files.list(java.nio.file.Paths.get("/")).collect(java.util.stream.Collectors.toList());
            }
        }
        '''

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[StreamResourceLeak] Streams that encapsulate a closeable resource should be closed using try-with-resources")
    }

    def 'compileJava fails when error-prone finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[ArrayEquals] Reference equality used to compare arrays")
    }

    def 'compileJava succeeds when error-prone finds no errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << validJavaFile

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'compileJava applies patches when error-prone finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-PerrorProneApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import java.util.Arrays;
        import java.util.Optional;
        public class Test {
            void test() {
                int[] a = {1, 2, 3};
                int[] b = {1, 2, 3};
                if (Arrays.equals(a, b)) {
                  System.out.println("arrays are equal!");
                  Optional.of("hello").orElseGet(() -> System.getProperty("world"));
                }
            }
        }
        '''.stripIndent()
    }

    def 'compileJava does not apply patches for error-prone checks that were turned OFF'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            tasks.withType(JavaCompile) {
                ${turnOffCheck}
            }
        """.stripIndent()
        file('src/main/java/test/Test.java') << '''
        package test;
        import org.slf4j.LoggerFactory;
        import org.slf4j.Logger;
        public class Test {
            void test() {
                Logger log = LoggerFactory.getLogger("foo");
                log.info("Hi there {}", "non safe arg");
            }
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-PerrorProneApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import org.slf4j.LoggerFactory;
        import org.slf4j.Logger;
        public class Test {
            void test() {
                Logger log = LoggerFactory.getLogger("foo");
                log.info("Hi there {}", "non safe arg");
            }
        }
        '''.stripIndent()

        where:
        turnOffCheck << [
                // via arg
                "options.errorprone.errorproneArgs += ['-Xep:Slf4jLogsafeArgs:OFF']",
                // via DSL
                """
                options.errorprone {
                    check('Slf4jLogsafeArgs', CheckSeverity.OFF)
                }                    
                """.stripIndent(),
        ]
    }
}
