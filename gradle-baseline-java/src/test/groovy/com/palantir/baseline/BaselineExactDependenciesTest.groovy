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

import com.palantir.gradle.plugintesting.GradleTestVersions
import nebula.test.multiproject.MultiProjectIntegrationInfo

import java.nio.file.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class BaselineExactDependenciesTest extends AbstractPluginTest {

    // language=Gradle
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-exact-dependencies'
            id 'com.palantir.baseline' apply false
            id 'com.palantir.consistent-versions' version '2.36.0' apply false
        }
    '''.stripIndent(true)

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

    def '#gradleVersion: both tasks work with different gradle versions'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/pkg/Foo.java') << minimalJavaFile

        then:
        with('checkUnusedDependencies', 'checkImplicitDependencies', '--stacktrace')
                .withGradleVersion(gradleVersion)
                .build()

        where:
        gradleVersion << GradleTestVersions.gradleVersionsForTests
    }

    def 'both tasks vacuously pass with no dependencies when entire baseline is applied'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            repositories {
                mavenCentral()
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
            implementation 'com.google.guava:guava:27.0.1-jre'
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

    @Unroll
    def '#task correctly picks up project dependency on java-library'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            implementation project(':needs-building-first')
        }
        """

        multiProject.addSubproject('needs-building-first', """
            apply plugin: 'java-library'
        """.stripIndent())

        file('needs-building-first/src/main/java/pkg/Bar.java') << """
            package pkg;
            public class Bar {}
        """.stripIndent()
        file('src/main/java/pkg/Foo.java') << """
            package pkg;
            class Foo {
                // Just reference something from the other project
                void test() { new Bar(); }
            }
        """.stripIndent()

        then:
        def result = with(":${task}", '--stacktrace').build()
        assert result.task(':needs-building-first:compileJava').getOutcome() != null

        where:
        task << ['checkUnusedDependencies', 'checkImplicitDependencies']
    }

    def 'checkUnusedDependencies is successful for multi-source project dep'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            implementation project(':needs-building-first')
        }
        """

        multiProject.addSubproject('needs-building-first', """
            apply plugin: 'java-library'
            apply plugin: 'scala'
        """.stripIndent())

        file('needs-building-first/src/main/java/pkg/Bar.java') << """
            package pkg;
            public class Bar {}
        """.stripIndent()
        file('src/main/java/pkg/Foo.java') << """
            package pkg;
            class Foo {
                // Just reference something from the other project
                void test() { new Bar(); }
            }
        """.stripIndent()

        then:
        def result = with("checkUnusedDependencies", '--stacktrace').build()
        result.task(':checkUnusedDependenciesMain').getOutcome() == TaskOutcome.SUCCESS
    }

    def 'checkUnusedDependenciesTest passes if main source set is not referenced in test'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            mavenCentral()
        }
        dependencies {
            implementation 'com.google.guava:guava:28.0-jre'
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

    def 'checkUnusedDependenciesTest passes if test fixture source set is not referenced in test'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        plugins {
            id 'java-test-fixtures'
        }
        """

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
            implementation 'com.fasterxml.jackson.datatype:jackson-datatype-guava:2.9.8' // pulls in guava transitively
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
        result.output.contains("project(':sub-project-no-deps')")
    }

    def 'checkImplicitDependencies should not report circular dependency on current project'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':sub-project-with-deps:checkImplicitDependencies', ':sub-project-no-deps:checkImplicitDependencies', '--stacktrace').withDebug(true).build()
        result.task(':sub-project-no-deps:checkImplicitDependencies').getOutcome() == TaskOutcome.SUCCESS
    }

    def 'check results can be up to date'() {
        when:
        setupMultiProject()
        with(":sub-project-no-deps:checkUnusedDependencies").build();

        then:
        BuildResult result = with(":sub-project-no-deps:checkUnusedDependencies").build();
        result.task(':sub-project-no-deps:checkUnusedDependencies').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'checkUnusedDependencies fails when a redundant project dep is present'() {
        when:
        setupMultiProject()

        then:
        BuildResult result = with(':checkUnusedDependencies', '--stacktrace').withDebug(true).buildAndFail()
        result.output.contains "project(':sub-project-with-deps') <-- main"
    }

    def 'plugin does not cause GCV checkUnusedConstraints to fail'() {
        setupMultiProject()
        buildFile << """
            apply plugin: 'com.palantir.consistent-versions'
        """.stripIndent()
        file('versions.props').text = ''

        expect:
        with(':checkUnusedConstraints', '--stacktrace', '--write-locks').withDebug(true).build()
    }

    def 'in Gradle >=8.3 you can set the toolchain language version without it being finalised'() {
        when:
        buildFile << standardBuildFile
        // language=Gradle
        buildFile << '''
            pluginManager.withPlugin('java') {
                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(17))
                    }
                }
            }
        '''.stripIndent(true)

        then:
        with('tasks', '--stacktrace').build()

        where:
        gradleVersion << GradleTestVersions.gradleVersionsForTests
    }

    def "Ensure checkUnusedDependencies works with GCV when project is excluded from GCV locks"() {
        given: 'we set up a build file where GCV is disabled for that project but the plugin is still applied'
        buildFile << standardBuildFile

        // language=Gradle
        buildFile << """
            apply plugin: 'com.palantir.consistent-versions'
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-exact-dependencies'
            
            repositories {
                mavenCentral()
            }
            
            // ensure compileClasspath is not locked in this project *but* GCV is still enabled
            versionsLock {
                disableJavaPluginDefaults()
            }
            
            dependencies {
                implementation 'com.google.guava:guava', {
                  version { strictly '33.4.7-jre' }
                }
                
                // pre #3194, this would fail to resolve for `compileClasspath` because 
                // `baseline-exact-dependencies-main` (a copy of `implementation`) would "steal" the 
                // `withDependenciesAction` that adds the constraints from `versions.props` to `implementation` for 
                // itself.  
                implementation 'com.palantir.tokens:auth-tokens'
            }
        """.stripIndent(true)

        file("versions.props") << """
            com.google.guava:guava = 33.4.8-jre
            com.palantir.tokens:* = 3.18.0
        """.stripIndent(true)

        file("src/main/java/com/p1/TestClassNoDeps.java") << """
            package com.p1;

            import java.util.Set;
            import com.google.common.collect.Iterables;
            import com.palantir.tokens.auth.BearerToken;
            
            class TestClassNoDeps {
                public void bla() {
                    Iterables.filter(Set.of(1, 2, 3), integer -> integer % 2 == 0);
                    BearerToken.valueOf("asdf");
                }
            }
        """

        with("writeVersionsLock").withGradleVersion(gradleVersion).build()

        when: 'we run checkUnusedDependencies'
        def result = with('checkUnusedDependencies', '--stacktrace', "--debug")
                .withGradleVersion(gradleVersion)
                .build()

        then: 'we do not see any failures'
        result.tasks(TaskOutcome.FAILED).isEmpty()

        where:
        gradleVersion << (["8.12.1", "8.14.3"] + GradleTestVersions.getGradleVersionsForTests()).unique()
    }

    /**
     * Sets up a multi-module project with 2 sub projects. The root project has a transitive dependency on sub-project-no-deps
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
            implementation project(':sub-project-with-deps')
        }
        """.stripIndent()

        def subProjects = multiProject.create(["sub-project-no-deps", "sub-project-with-deps"])

        //properly declare dependency between two sub-projects
        subProjects['sub-project-with-deps'].buildGradle << '''
            apply plugin: 'java-library'
            
            dependencies {
                api project(':sub-project-no-deps')
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
