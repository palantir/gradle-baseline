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
import spock.lang.IgnoreIf

@IgnoreIf({ Integer.parseInt(jvm.javaSpecificationVersion) < 14 })
class BaselineEnablePreviewFlagTest extends IntegrationSpec {

    def setup() {
        buildFile << '''
            apply plugin: 'java-library'
            apply plugin: 'com.palantir.baseline-enable-preview-flag'
            apply plugin: 'application'
            
            application {
              mainClass = 'foo.Foo'
            }  
        '''.stripIndent()

        file('src/main/java/foo/Foo.java') << '''
            package foo;
            public class Foo {
              public record Coordinate(int x, int y) {}
            
              public static void main(String... args) {
                System.out.println("Hello, world: " + new Coordinate(1, 2));
              }
            }
        '''.stripIndent()
    }

    def 'compiles'() {
        when:
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
        ExecutionResult executionResult = runTasks('run', '-is')

        then:
        assert executionResult.getStandardOutput().contains("Hello, world: Coordinate[x=1, y=2]")
    }

    def 'testing works'() {
        when:
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
}

