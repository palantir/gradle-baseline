/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class BaselineImmutablesTest extends IntegrationSpec {
    private static final String IMMUTABLES = 'org.immutables:value:2.8.8'
    private static final String IMMUTABLES_ANNOTATIONS = 'org.immutables:value:2.8.8:annotations'

    def 'inserts incremental compilation args into source sets that have immutables'() {
        buildFile << """
            plugins {
                id 'org.unbroken-dome.test-sets' version '4.0.0'
            }

            apply plugin: 'com.palantir.baseline-immutables'
            apply plugin: 'java-library'

            repositories {
                mavenCentral()
            }
            
            testSets {
                hasImmutables
                doesNotHaveImmutables
                hasImmutablesAddedInAfterEvaluate
                onlyHasImmutablesAnnotations
            }
            
            afterEvaluate {
                dependencies {
                    hasImmutablesAddedInAfterEvaluateAnnotationProcessor '$IMMUTABLES'
                }
            }
            
            dependencies {
                annotationProcessor '$IMMUTABLES'
                
                hasImmutablesAnnotationProcessor '$IMMUTABLES'

                onlyHasImmutablesAnnotationsAnnotationProcessor '$IMMUTABLES_ANNOTATIONS'
            }
            
            task compileAll
            
            tasks.withType(JavaCompile) { javaCompile ->
                doFirst {
                    logger.lifecycle "\${javaCompile.name}: \${javaCompile.options.allCompilerArgs}"
                }
                                
                tasks.compileAll.dependsOn javaCompile
            }
        """.stripIndent()

        ['main', 'hasImmutables', 'doesNotHaveImmutables', 'hasImmutablesAddedInAfterEvaluate', 'onlyHasImmutablesAnnotations'].each {
            writeJavaSourceFile '''
                public class Foo {}
            '''.stripIndent(), "src/$it/java"
        }

        when:
        def stdout = runTasksSuccessfully('compileAll').standardOutput
        println stdout

        then:
        stdout.contains 'compileJava: [-Aimmutables.gradle.incremental]'
        stdout.contains 'compileHasImmutablesJava: [-Aimmutables.gradle.incremental]'
        stdout.contains 'compileDoesNotHaveImmutablesJava: []'
        stdout.contains 'compileHasImmutablesAddedInAfterEvaluateJava: [-Aimmutables.gradle.incremental]'
        stdout.contains 'compileOnlyHasImmutablesAnnotationsJava: []'
    }

    def 'Compatible with java #javaVersion'() {
        when:
        buildFile << """
            apply plugin: 'com.palantir.baseline-java-versions'
            apply plugin: 'com.palantir.baseline-immutables'
            apply plugin: 'java-library'
            repositories {
                mavenCentral()
            }
            tasks.withType(JavaCompile).configureEach({
              options.compilerArgs += ['-Werror']
            })
            javaVersions {
                libraryTarget = $javaVersion
            }
            dependencies {
                annotationProcessor '$IMMUTABLES'
                compileOnly '$IMMUTABLES_ANNOTATIONS'
            }
        """.stripIndent()
        writeJavaSourceFile("""
        package com.palantir.one;
        import com.palantir.two.ImmutableTwo;
        import org.immutables.value.Value;
        @Value.Immutable
        public interface One {
            ImmutableTwo two();
        }
        """.stripIndent())
        writeJavaSourceFile("""
        package com.palantir.two;
        import org.immutables.value.Value;
        @Value.Immutable
        public interface Two {
            String value();
        }
        """.stripIndent())
        then:
        ExecutionResult result = runTasks('compileJava')
        println(result.standardError)
        result.success

        where:
        javaVersion << ImmutableList.of(11, 17)
    }

    def 'handles an annotationProcesor source set extending from another one'() {
        buildFile << """
            apply plugin: 'com.palantir.baseline-immutables'
            apply plugin: 'java-library'

            repositories {
                mavenCentral()
            }

            dependencies {
                annotationProcessor '$IMMUTABLES'
                compileOnly '$IMMUTABLES_ANNOTATIONS'
            }
            
            configurations {
                testAnnotationProcessor.extendsFrom annotationProcessor
                testCompileOnly.extendsFrom compileOnly
            } 
            
            tasks.withType(JavaCompile) { javaCompile ->
                doFirst {
                    logger.lifecycle "\${javaCompile.name}: \${javaCompile.options.allCompilerArgs}"
                }
            }
        """.stripIndent(true)

        def testJava = 'src/test/java'

        // language=java
        writeJavaSourceFile '''
            package test;
            import org.immutables.value.Value;
            @Value.Immutable
            public interface Test {
                int item();
            }
        '''.stripIndent(true), testJava

        when:
        def stdout = runTasksSuccessfully('compileTestJava').standardOutput
        println stdout

        then:
        stdout.contains('compileTestJava: [-Aimmutables.gradle.incremental]')
    }
}
