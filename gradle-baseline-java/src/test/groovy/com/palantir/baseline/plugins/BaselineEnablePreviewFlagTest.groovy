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

package com.palantir.baseline.plugins


import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.gradle.api.JavaVersion
import spock.lang.IgnoreIf

@IgnoreIf({JavaVersion.current() < JavaVersion.VERSION_14})
class BaselineEnablePreviewFlagTest extends IntegrationSpec {

    def setupSingleProject(File dir) {
        new File(dir, "build.gradle") << '''
            apply plugin: 'java-library'
            apply plugin: 'com.palantir.baseline-enable-preview-flag'
            apply plugin: 'application'
            
            application {
              mainClass = 'foo.Foo'
            }  
        '''.stripIndent()

        writeSourceFileContainingRecord(dir);
    }

    private writeSourceFileContainingRecord(File dir) {
        writeJavaSourceFile('''
            package foo;
            public class Foo {
              public record Coordinate(int x, int y) {}
            
              public static void main(String... args) {
                System.out.println("Hello, world: " + new Coordinate(1, 2));
              }
            }
        '''.stripIndent(), dir)
    }

    def 'compiles'() {
        when:
        setupSingleProject(projectDir)
        buildFile << '''
        tasks.classes.doLast {
          println "COMPILED:" + new File(sourceSets.main.java.outputDir, "foo").list()
        }
        '''
        ExecutionResult executionResult = runTasks('classes', '-is')

        then:
        executionResult.getStandardOutput().contains('Foo$Coordinate.class')
        executionResult.getStandardOutput().contains('Foo.class')
    }

    def 'runs'() {
        when:
        setupSingleProject(projectDir)
        ExecutionResult executionResult = runTasks('run', '-is')

        then:
        assert executionResult.getStandardOutput().contains("Hello, world: Coordinate[x=1, y=2]")
    }

    def 'testing works'() {
        when:
        setupSingleProject(projectDir)
        buildFile << '''
        repositories { mavenCentral() }
        dependencies {
          testImplementation 'junit:junit:4.13.1' 
        }
        '''

        file('src/test/java/foo/FooTest.java') << '''
            package foo;
            public final class FooTest {
              @org.junit.Ignore("silly junit4 thinks this 'class' actually contains tests")
              record Whatever(int x, int y) {}

              @org.junit.Test
              public void whatever() {
                Foo.main();
                System.out.println("Hello, world: " + new Whatever(1, 2));
              }
            }
        '''.stripIndent()
        ExecutionResult executionResult = runTasks('test', '-is')

        then:
        executionResult.getStandardOutput().contains("Hello, world: Coordinate[x=1, y=2]")
        executionResult.getStandardOutput().contains("Hello, world: Whatever[x=1, y=2]")
    }

    def 'multiproject'() {
        when:
        File java14Dir = addSubproject("my-java-14", '''
            apply plugin: 'java-library'
            apply plugin: 'application'
            
            sourceCompatibility = 14
            application {
              mainClass = 'foo.Foo'
            }
            dependencies {
              implementation project(':my-java-14-preview')
            }
            ''')

        writeJavaSourceFile('''
            package bar;
            public class Bar {
              public static void main(String... args) {
                foo.Foo.main();
              }
            }
            ''', java14Dir);

        File java14PreviewDir = addSubproject("my-java-14-preview", '''
            apply plugin: 'java-library'
            apply plugin: 'com.palantir.baseline-enable-preview-flag'
            
            sourceCompatibility = 14
            ''')

        writeSourceFileContainingRecord(java14PreviewDir)

        then:
        ExecutionResult executionResult = runTasks('run', '-is')
        println executionResult.getStandardError()
    }
}

