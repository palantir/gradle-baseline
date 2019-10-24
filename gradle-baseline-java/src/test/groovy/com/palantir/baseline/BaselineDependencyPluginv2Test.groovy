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


import com.palantir.baseline.tasks.dependencies.AbstractDependencyTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineDependencyPluginv2Test extends AbstractDependencyTest {

    def minimalJavaFile = '''
    package pkg;
    public class Foo { void foo() {} }
    '''.stripIndent()

    def 'both tasks vacuously pass with no dependencies'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        with('checkUnusedDeps', 'checkImplicitDeps', '--stacktrace').build()
    }

    def 'tasks are not run as part of ./gradlew check'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        BuildResult result = with('check').build()
        result.task(':checkUnusedDeps') == null
        result.task(':checkImplicitDeps ') == null
    }

    def 'checkUnusedDeps fails when no classes are referenced'() {
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
        BuildResult result = with('checkUnusedDeps', '--stacktrace').buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkUnusedDeps').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 dependencies unused during compilation")
    }

    def 'checkUnusedDeps passes when annotationProcessor or compileOnly classes are not referenced'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            mavenCentral()
        }
        dependencies {
            annotationProcessor 'org.immutables:value:2.7.5'
            compileOnly 'org.immutables:value:2.7.5:annotations'
        }
        """
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        BuildResult result = with('checkUnusedDeps', '--stacktrace').build()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkUnusedDeps').getOutcome() == TaskOutcome.SUCCESS
    }

    def 'checkImplicitDeps fails when a class is imported without being declared as a dependency'() {
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
        BuildResult result = with('checkImplicitDeps', '--stacktrace').buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkImplicitDeps').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 implicit dependencies")
    }

    def 'checkImplicitDeps succeeds when cross-project dependencies properly declared'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':sub-project-with-deps:checkImplicitDeps', '--stacktrace').withDebug(true).build()
        result.task(':sub-project-with-deps:classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':sub-project-with-deps:checkImplicitDeps').getOutcome() == TaskOutcome.SUCCESS

    }

    def 'checkImplicitDeps fails on transitive project dependency'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with('checkImplicitDeps', '--stacktrace').withDebug(true).buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkImplicitDeps').getOutcome() == TaskOutcome.FAILED
        result.output.contains("implementation project(':sub-project-no-deps')")
    }

    def 'checkImplicitDeps should not report circular dependency on current project'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':sub-project-with-deps:checkImplicitDeps', ':sub-project-no-deps:checkImplicitDeps', '--stacktrace').withDebug(true).build()
        result.task(':sub-project-no-deps:checkImplicitDeps').getOutcome() == TaskOutcome.SUCCESS
    }
}
