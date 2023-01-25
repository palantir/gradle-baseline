/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import nebula.test.IntegrationSpec;

class BaselineDeadCodeIntegrationTest extends IntegrationSpec {


    def standardBuildFile = '''
        buildscript {
            repositories { mavenCentral() }
            dependencies {
                classpath 'com.netflix.nebula:nebula-publishing-plugin:17.0.0'
            }
        }
        plugins {
            id 'java-library'
            id 'application'
        }
        
        apply plugin: 'com.palantir.baseline-dead-code'
        
        repositories {
            mavenCentral()
        }
        
        application {
            mainClass = 'Main'
        }
    '''.stripIndent(true)

    def mainClass = '''
        import java.util.Optional;
        
        public class Main { 
            public static void main(String[] args) {
                Optional.of(args).isEmpty();
            }
        }
        '''.stripIndent(true)
    def redundantClass = '''
        public class RedundantClass {
            public boolean foo() {
                return 4;
            }
        }
        '''.stripIndent(true)

    def setup() {
//        setFork(true)

        buildFile << standardBuildFile
    }


    def 'run proguard something happens'() {
        when:
//        buildFile << '''
//        javaVersions {
//            libraryTarget = 8
//            runtime = 11
//        }
//        '''.stripIndent(true)
        file('src/main/java/Main.java') << mainClass
        file('src/main/java/RedundantClass.java') << redundantClass
        !file("$projectDir/build/proguard").exists()

        then:
        def result = runTasksSuccessfully('proguard')
        result.wasExecuted(":jar")

        file("$projectDir/build/proguard/printusage").exists()
        file("$projectDir/build/proguard/printconfiguration").exists()
        file("$projectDir/build/proguard/printseeds").exists()
        file("$projectDir/build/proguard/out").isDirectory()

    }

    // TODO(dfox): test re-runs with identical java code shouldn't re-run proguard
    // TODO(dfox): multiproject build with `project(':foo')` dependencies, using conjure, GCV, sls-packaging, baseline-java-version
    // TODO(dfox): project with build cache!
    // TODO(dfox): project using immutables annotation processing.
    // TODO(dfox): project using dagger annotation processing.
    // TODO(dfox): project with lots of important 'resources'
    // TODO(dfox): project with some classes which are identically named (but different packages?)
}
