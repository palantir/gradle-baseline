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

package com.palantir.baseline;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@GradlePluginTests
public class BaselineExactDependenciesTest {

    private String minimalJavaFile() {
        return """
            package pkg;
            public class Foo { void foo() {} }
            """;
    }

    @BeforeEach
    void beforeEach(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.baseline-exact-dependencies");
    }

    @Test
    public void both_tasks_vacuously_pass_with_no_dependencies(GradleInvoker gradle, RootProject rootProject) {
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        gradle.withArgs("checkUnusedDependencies", "checkImplicitDependencies").buildsSuccessfully();
    }

    @Test
    public void both_tasks_work_with_different_gradle_versions(GradleInvoker gradle, RootProject rootProject) {
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        gradle.withArgs("checkUnusedDependencies", "checkImplicitDependencies").buildsSuccessfully();
    }

    @Test
    public void both_tasks_vacuously_pass_with_no_dependencies_when_entire_baseline_is_applied(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
                mavenLocal() // for baseline-error-prone
            }
            """);
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        gradle.withArgs("checkUnusedDependencies", "checkImplicitDependencies").buildsSuccessfully();
    }

    @Test
    public void tasks_are_not_run_as_part_of_gradlew_check(GradleInvoker gradle, RootProject rootProject) {
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        InvocationResult result = gradle.withArgs("check").buildsSuccessfully();
        assertThat(result).task(":checkUnusedDependencies").notOnTaskGraph();
        assertThat(result).task(":checkImplicitDependencies").notOnTaskGraph();
    }

    @Test
    public void checkUnusedDependencies_fails_when_no_classes_are_referenced(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'com.google.guava:guava:27.0.1-jre'
            }
            """);
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsWithFailure();
        assertThat(result).task(":classes").succeeded();
        assertThat(result).task(":checkUnusedDependenciesMain").failed();
        assertThat(result).output().contains("Found 1 dependencies unused during compilation");
    }

    @Test
    public void checkUnusedDependencies_passes_when_annotationProcessor_or_compileOnly_classes_are_not_referenced(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                annotationProcessor 'org.immutables:value:2.7.5'
                compileOnly 'org.immutables:value:2.7.5:annotations'
            }
            """);
        rootProject.mainSourceSet().java().writeClass(minimalJavaFile());

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsSuccessfully();
        assertThat(result).task(":classes").succeeded();
        assertThat(result).task(":checkUnusedDependencies").succeeded();
        assertThat(result).task(":checkUnusedDependenciesMain").succeeded();
    }

    @ParameterizedTest
    @ValueSource(strings = {"checkUnusedDependencies", "checkImplicitDependencies"})
    public void task_correctly_picks_up_project_dependency_on_java_library(
            String task, GradleInvoker gradle, RootProject rootProject, SubProject needsBuildingFirst) {
        rootProject.buildGradle().append("""
            dependencies {
                implementation project(':needsBuildingFirst')
            }
            """);

        needsBuildingFirst.buildGradle().plugins().add("java-library");

        needsBuildingFirst.mainSourceSet().java().writeClass("""
            package pkg;
            public class Bar {}
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package pkg;
            class Foo {
                // Just reference something from the other project
                void test() { new Bar(); }
            }
            """);

        InvocationResult result = gradle.withArgs(":" + task).buildsSuccessfully();
        assertThat(result).task(":needsBuildingFirst:compileJava").succeeded();
    }

    @Test
    public void checkUnusedDependencies_is_successful_for_multi_source_project_dep(
            GradleInvoker gradle, RootProject rootProject, SubProject needsBuildingFirst) {
        rootProject.buildGradle().append("""
            dependencies {
                implementation project(':needsBuildingFirst')
            }
            """);

        needsBuildingFirst.buildGradle().plugins().add("java-library");
        needsBuildingFirst.buildGradle().plugins().add("scala");

        needsBuildingFirst.mainSourceSet().java().writeClass("""
            package pkg;
            public class Bar {}
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package pkg;
            class Foo {
                // Just reference something from the other project
                void test() { new Bar(); }
            }
            """);

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsSuccessfully();
        assertThat(result).task(":checkUnusedDependenciesMain").succeeded();
    }

    @Test
    public void checkUnusedDependenciesTest_passes_if_main_source_set_is_not_referenced_in_test(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'com.google.guava:guava:28.0-jre'
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package pkg;
            public class Foo {
                void foo() {
                    com.google.common.collect.ImmutableList.of();
                }
            }
            """);

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsSuccessfully();
        assertThat(result).task(":checkUnusedDependenciesTest").succeeded();
    }

    @Test
    public void checkUnusedDependenciesTest_passes_if_test_fixture_source_set_is_not_referenced_in_test(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java-test-fixtures");

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsSuccessfully();
        assertThat(result).task(":checkUnusedDependenciesTest").succeeded();
    }

    @Test
    public void checkImplicitDependencies_fails_when_a_class_is_imported_without_being_declared_as_a_dependency(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'com.fasterxml.jackson.datatype:jackson-datatype-guava:2.9.8' // pulls in guava transitively
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package pkg;
            public class Foo {
                void foo() {
                    com.google.common.collect.ImmutableList.of();
                }
            }
            """);

        InvocationResult result = gradle.withArgs("checkImplicitDependencies").buildsWithFailure();
        assertThat(result).task(":classes").succeeded();
        assertThat(result).task(":checkImplicitDependenciesMain").failed();
        assertThat(result).output().contains("Found 1 implicit dependencies");
    }

    @Test
    public void checkImplicitDependencies_succeeds_when_cross_project_dependencies_properly_declared(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);

        InvocationResult result =
                gradle.withArgs(":subProjectWithDeps:checkImplicitDependencies").buildsSuccessfully();
        assertThat(result).task(":subProjectWithDeps:classes").succeeded();
        assertThat(result).task(":subProjectWithDeps:checkImplicitDependencies").succeeded();
    }

    @Test
    public void checkImplicitDependencies_fails_on_transitive_project_dependency(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);

        InvocationResult result = gradle.withArgs("checkImplicitDependencies").buildsWithFailure();
        assertThat(result).task(":classes").succeeded();
        assertThat(result).task(":checkImplicitDependenciesMain").failed();
        assertThat(result).output().contains("Found 1 implicit dependencies");
        assertThat(result).output().contains("project(':subProjectNoDeps')");
    }

    @Test
    public void checkImplicitDependencies_should_not_report_circular_dependency_on_current_project(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);

        InvocationResult result = gradle.withArgs(
                        ":subProjectWithDeps:checkImplicitDependencies", ":subProjectNoDeps:checkImplicitDependencies")
                .buildsSuccessfully();
        assertThat(result).task(":subProjectNoDeps:checkImplicitDependencies").succeeded();
    }

    @Test
    public void check_results_can_be_up_to_date(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);
        gradle.withArgs(":subProjectNoDeps:checkUnusedDependencies").buildsSuccessfully();

        InvocationResult result =
                gradle.withArgs(":subProjectNoDeps:checkUnusedDependencies").buildsSuccessfully();
        assertThat(result).task(":subProjectNoDeps:checkUnusedDependencies").upToDate();
    }

    @Test
    public void checkUnusedDependencies_fails_when_a_redundant_project_dep_is_present(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);

        InvocationResult result = gradle.withArgs("checkUnusedDependencies").buildsWithFailure();
        assertThat(result).output().contains("project(':subProjectWithDeps') <-- main");
    }

    @Test
    public void plugin_does_not_cause_gcv_checkUnusedConstraints_to_fail(
            GradleInvoker gradle, RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        setupMultiProject(rootProject, subProjectNoDeps, subProjectWithDeps);
        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");
        rootProject.file("versions.props").overwrite("");

        gradle.withArgs("checkUnusedConstraints", "--write-locks").buildsSuccessfully();
    }

    @Test
    public void in_Gradle_8_3_and_later_you_can_set_the_toolchain_language_version_without_it_being_finalised(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            pluginManager.withPlugin('java') {
                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(17))
                    }
                }
            }
            """);

        gradle.withArgs("tasks").buildsSuccessfully();
    }

    @Test
    public void ensure_checkUnusedDependencies_works_with_gcv_when_project_is_excluded_from_gcv_locks(
            GradleInvoker gradle, RootProject rootProject) {
        // we set up a build file where GCV is disabled for that project but the plugin is still applied
        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.baseline-exact-dependencies");
        rootProject.buildGradle().append("""
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
            """);

        rootProject.file("versions.props").overwrite("""
            com.google.guava:guava = 33.4.8-jre
            com.palantir.tokens:* = 3.18.0
            """);

        rootProject.mainSourceSet().java().writeClass("""
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
            """);

        gradle.withArgs("writeVersionsLock").buildsSuccessfully();

        gradle.withArgs("checkUnusedDependencies", "--debug").buildsSuccessfully();
    }

    /**
     * Sets up a multi-module project with 2 subprojects. The root project has a transitive dependency
     * on subProjectNoDeps and so checkImplicitDependencies should fail on it.
     */
    private void setupMultiProject(
            RootProject rootProject, SubProject subProjectNoDeps, SubProject subProjectWithDeps) {
        // Apply plugins to all projects individually
        Stream.of(rootProject, subProjectNoDeps, subProjectWithDeps).forEach(project -> {
            project.buildGradle().plugins().add("java");
            project.buildGradle().plugins().add("com.palantir.baseline-exact-dependencies");
        });

        rootProject.buildGradle().append("""
            dependencies {
                implementation project(':subProjectWithDeps')
            }
            """);

        // SubProject names are determined by parameter names

        // properly declare dependency between two sub-projects
        subProjectWithDeps.buildGradle().plugins().add("java-library");
        subProjectWithDeps.buildGradle().append("""
            dependencies {
                api project(':subProjectNoDeps')
            }
            """);

        // subProjectNoDeps has no dependencies
        subProjectNoDeps.mainSourceSet().java().writeClass("package com.p1; public class TestClassNoDeps {}");

        // write a second class to be referenced in a different place
        subProjectNoDeps.mainSourceSet().java().writeClass("package com.p1; public class TestClassNoDeps2 {}");

        // write class in subProjectWithDeps that uses TestClassNoDeps
        subProjectWithDeps.mainSourceSet().java().writeClass("""
            package com.p2;
            import com.p1.TestClassNoDeps;
            public class TestClassWithDeps {
                void foo() {
                    System.out.println (new TestClassNoDeps());
                }
            }
            """);

        // Create source file in root project that uses TestClassNoDeps2
        rootProject.mainSourceSet().java().writeClass("""
            package com.p2;
            import com.p1.TestClassNoDeps2;
            public class RootTestClassWithDeps {
                void foo() {
                    System.out.println (new TestClassNoDeps2());
                }
            }
            """);
    }
}
