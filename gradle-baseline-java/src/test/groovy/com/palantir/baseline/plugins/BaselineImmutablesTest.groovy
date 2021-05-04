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

import nebula.test.IntegrationSpec

class BaselineImmutablesTest extends IntegrationSpec {
    private static final String IMMUTABLES = 'org.immutables:value:2.8.8'

    def 'should insert args into the correct places'() {
        buildFile << """
            plugins {
                id 'org.unbroken-dome.test-sets' version '3.0.1'
            }

            apply plugin: 'com.palantir.baseline-immutables'
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            
            testSets {
                hasImmutables
                doesNotHaveImmutables
                hasImmutablesAddedInAfterEvaluate
            }
            
            afterEvaluate {
                dependencies {
                    hasImmutablesAddedInAfterEvaluateAnnotationProcessor '$IMMUTABLES'
                }
            }
            
            dependencies {
                annotationProcessor '$IMMUTABLES'
                
                hasImmutablesAnnotationProcessor '$IMMUTABLES'
            }
            
            task compileAll
            
            tasks.withType(JavaCompile) { javaCompile ->
                doFirst {
                    logger.lifecycle "\${javaCompile.name}: \${javaCompile.options.allCompilerArgs}"
                }
                                
                tasks.compileAll.dependsOn javaCompile
            }
        """.stripIndent()

        ['main', 'hasImmutables', 'doesNotHaveImmutables', 'hasImmutablesAddedInAfterEvaluate'].each {
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
    }
}
