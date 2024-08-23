/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.suppressibleerrorprone

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class SuppressibleErrorPronePluginIntegrationTest extends IntegrationSpec {
    File appJava

    def setup() {
        // language=Gradle
        buildFile << '''
            apply plugin: 'com.palantir.suppressible-error-prone'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
                mavenLocal()
            }
            
            dependencies {
                errorprone 'com.google.errorprone:error_prone_core:2.28.0'
            }
        '''.stripIndent(true)

        appJava = file('src/main/java/app/App.java')
    }

    def 'reports a failing error prone'() {
        // language=Java
        writeJavaSourceFile '''
            package app;
            public final class App {
                public static void main(String[] args) {
                    System.out.println(new int[3].toString());
                }
            }
        '''.stripIndent(true)

        when:
        def stderr = runTasksWithFailure('compileJava').standardError

        then:
        stderr.contains('[ArrayToString]')
    }

    def 'can suppress an error prone with for-rollout prefix'() {
        // This test is explicitly checking we suppress the for-rollout prefix as that is what exists
        // in people's codebases

        when:
        // language=Java
        writeJavaSourceFile '''
            package app;
            public final class App {
                @SuppressWarnings("for-rollout:ArrayToString")
                public static void main(String[] args) {
                    System.out.println(new int[3].toString());
                }
            }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('compileJava')
    }

    def 'ensure warnings are disabled in generated code'() {
        when:
        // language=Java
        writeJavaSourceFile '''
            package app;
            import javax.annotation.processing.Generated;
            @Generated("com.place.SomeProcessor")
            public final class App {
                public static void main(String[] args) {
                    System.out.println(new int[3].toString());
                }
            }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('compileJava')
    }

    def 'can apply patches for a check if added to the patchChecks list'() {
        // language=Gradle
        buildFile << '''
            suppressibleErrorProne {
                patchChecks.add('ArrayToString')
            }
        '''.stripIndent(true)

        // language=Java
        writeJavaSourceFile '''
            package app;
            public final class App {
                public static void main(String[] args) {
                    System.out.println(new int[3].toString());
                }
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully('compileJava', '-PerrorProneApply')

        then:
        runTasksSuccessfully('compileJava')

        appJava.text.contains('Arrays.toString(new int[3])')
    }

    // TODO(callumr): Even if the check is not in the patches list?
    def 'can suppress a failing check'() {
        // language=Java
        writeJavaSourceFile '''
            package app;
            public final class App {
                public static void main(String[] args) {
                    System.out.println(new int[3].toString());
                }
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully('compileJava', '-PerrorProneSuppressStage1')
        runTasksSuccessfully('compileJava', '-PerrorProneSuppressStage2')

        then:
        runTasksSuccessfully('compileJava')

        appJava.text.contains('@SuppressWarnings(\"for-rollout:ArrayToString\")')
    }

    @Override
    ExecutionResult runTasksSuccessfully(String... tasks) {
        def result = runTasks(tasks)
        println result.standardError
        println result.standardOutput
        result.rethrowFailure()
    }

    @Override
    ExecutionResult runTasks(String... tasks) {
        def projectVersion = Optional.ofNullable(System.getProperty('projectVersion')).orElseThrow()
        String[] strings = tasks + ["-PsuppressibleErrorProneVersion=${projectVersion}".toString()]
        return super.runTasks(strings)
    }
}
