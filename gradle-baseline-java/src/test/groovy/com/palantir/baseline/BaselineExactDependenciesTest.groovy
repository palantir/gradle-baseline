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

import java.nio.file.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineExactDependenciesTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-exact-dependencies'
            id 'com.palantir.baseline' apply false
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

    def 'both tasks vacuously pass with no dependencies when entire baseline is applied'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            repositories {
                jcenter()
                mavenLocal() // for baseline-error-prone
            }
            apply plugin: 'com.palantir.baseline'
        """.stripIndent()
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
        result.task(':checkUnusedDependenciesMain').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 dependencies unused during compilation")
    }

    def 'checkUnusedDependencies passes when annotationProcessor or compileOnly classes are not referenced'() {
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
        BuildResult result = with('checkUnusedDependencies', '--stacktrace').build()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkUnusedDependencies').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkUnusedDependenciesMain').getOutcome() == TaskOutcome.SUCCESS
    }

    def 'checkUnusedDependenciesTest passes if dependency from main source set is not referenced in test'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            mavenCentral()
        }
        dependencies {
            compile 'com.google.guava:guava:28.0-jre'
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
        def result = with('checkUnusedDependencies', '--stacktrace').build()
        result.task(':checkUnusedDependenciesTest').getOutcome() == TaskOutcome.SUCCESS
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
        result.task(':checkImplicitDependenciesMain').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 implicit dependencies")
    }

    def 'checkImplicitDependencies succeeds when cross-project dependencies properly declared'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':sub-project-with-deps:checkImplicitDependencies', '--stacktrace').withDebug(true).build()
        result.task(':sub-project-with-deps:classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':sub-project-with-deps:checkImplicitDependencies').getOutcome() == TaskOutcome.SUCCESS

    }

    def 'checkImplicitDependencies fails on transitive project dependency'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with('checkImplicitDependencies', '--stacktrace').withDebug(true).buildAndFail()
        result.task(':classes').getOutcome() == TaskOutcome.SUCCESS
        result.task(':checkImplicitDependenciesMain').getOutcome() == TaskOutcome.FAILED
        result.output.contains("Found 1 implicit dependencies")
        result.output.contains("implementation project(':sub-project-no-deps')")
    }

    def 'checkImplicitDependencies should not report circular dependency on current project'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':sub-project-with-deps:checkImplicitDependencies', ':sub-project-no-deps:checkImplicitDependencies', '--stacktrace').withDebug(true).build()
        result.task(':sub-project-no-deps:checkImplicitDependencies').getOutcome() == TaskOutcome.SUCCESS
    }

    def 'checkUnusedDependencies fails when a redundant project dep is present'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':checkUnusedDependencies', '--stacktrace').withDebug(true).buildAndFail()
        result.output.contains "project(':sub-project-with-deps') (sub-project-with-deps.jar (project :sub-project-with-deps))"
        result.output.contains "implementation project(':sub-project-no-deps')"
    }

    /**
     * Sets up a multi-module project with 2 sub projects.  The root project has a transitive dependency on sub-project-no-deps
     * and so checkImplicitDependencies should fail on it.
     */
    private void setupMultiProject() {
        buildFile << standardBuildFile
        buildFile << """
        allprojects {
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-exact-dependencies'
        }
        dependencies {
            compile project(':sub-project-with-deps')
        }
        """.stripIndent()

        def subProjects = multiProject.create(["sub-project-no-deps", "sub-project-with-deps"])

        //properly declare dependency between two sub-projects
        subProjects['sub-project-with-deps'].buildGradle << '''
            dependencies {
                compile project(':sub-project-no-deps')
            }
        '''.stripIndent()

        //sub-project-no-deps has no dependencies
        def directory = subProjects['sub-project-no-deps'].directory
        File myClass1 = new File(directory, "src/main/java/com/p1/TestClassNoDeps.java")
        Files.createDirectories(myClass1.toPath().getParent())
        myClass1 << "package com.p1; public class TestClassNoDeps {}"

        //write a second class to be referenced in a different place
        myClass1 = new File(directory, "src/main/java/com/p1/TestClassNoDeps2.java")
        myClass1 << "package com.p1; public class TestClassNoDeps2 {}"

        //write class in sub-project-with-deps that uses TestClassNoDeps
        File myClass2 = new File(subProjects['sub-project-with-deps'].directory, "src/main/java/com/p2/TestClassWithDeps.java")
        Files.createDirectories(myClass2.toPath().getParent())
        myClass2 << '''
        package com.p2;
        import com.p1.TestClassNoDeps;
        public class TestClassWithDeps {
            void foo() {
                System.out.println (new TestClassNoDeps());
            }
        }
        '''.stripIndent()

        //Create source file in root project that uses TestClassNoDeps2
        File myRootClass = new File(projectDir, "src/main/java/com/p0/RootTestClassWithDeps.java")
        Files.createDirectories(myRootClass.toPath().getParent())
        myRootClass << '''
        package com.p2;
        import com.p1.TestClassNoDeps2;
        public class RootTestClassWithDeps {
            void foo() {
                System.out.println (new TestClassNoDeps2());
            }
        }
        '''.stripIndent()

    }
}
