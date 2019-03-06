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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineExactDependenciesTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-exact-dependencies'
        }
    '''.stripIndent()

    def minimalJavaFile = '''
    package pkg;
    public class Foo { void foo() {} }
    '''.stripIndent()

    def 'both tasks vacuously pass with no dependencies'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        with('checkUnusedDependencies', 'checkImplicitDependencies', '--stacktrace').build()
    }

    def 'tasks are not run as part of ./gradlew check'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        BuildResult result = with('check').build()
        result.task(':checkUnusedDependencies') == null
        result.task(':checkImplicitDependencies ') == null
    }

    def 'checkUnusedDependencies fails when no classes are referenced'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            mavenCentral()
        }
        dependencies {
            compile 'com.google.guava:guava:27.0.1-jre'
        }
        """
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        BuildResult result = with('checkUnusedDependencies', '--stacktrace').buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkUnusedDependencies').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 dependencies unused during compilation")
    }

    def 'checkImplicitDependencies fails when a class is imported without being declared as a dependency'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            mavenCentral()
        }
        dependencies {
            compile 'com.fasterxml.jackson.datatype:jackson-datatype-guava:2.9.8' // pulls in guava transitively
        }
        """
        file('src/main/java/pkg/Foo.java') << '''
        package pkg;
        public class Foo {
            void foo() {
                com.google.common.collect.ImmutableList.of();
            }
        }
        '''.stripIndent()

        then:
        BuildResult result = with('checkImplicitDependencies', '--stacktrace').buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkImplicitDependencies').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 implicit dependencies")
    }
}
