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

package com.palantir.baseline.plugins;

import com.palantir.baseline.tasks.CheckJUnitDependencies;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.base.TestingExtension;
import org.gradle.util.GradleVersion;

public final class BaselineTesting implements Plugin<Project> {

    private static final GradleVersion GRADLE_8 = GradleVersion.version("8.0");

    private static final String JUNIT_JUPITER = "org.junit.jupiter:junit-jupiter";
    private static final String JUNIT_PLATFORM_LAUNCHER = "org.junit.platform:junit-platform-launcher";

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CrashOnOutOfMemoryError");

            if (!Objects.equals("true", project.findProperty("com.palantir.baseline.restore-test-cache"))) {
                // Never cache test tasks, until we work out the correct inputs for ETE / integration tests
                task.getOutputs().cacheIf(t -> false);
            }

            // repos that use 'snapshot' style testing should all use one convenient task to refresh the snapshots,
            // ./gradlew test -Drecreate=true
            boolean shouldRecreate = Boolean.getBoolean("recreate");
            task.systemProperty("recreate", Boolean.toString(shouldRecreate));
            if (shouldRecreate) {
                task.getOutputs().upToDateWhen(t -> false);
            }
        });

        project.getPluginManager().withPlugin("java", unusedPlugin -> {
            TestingExtension testingExtension = project.getExtensions().getByType(TestingExtension.class);

            TaskProvider<CheckJUnitDependencies> checkJUnitDependencies =
                    project.getTasks().register("checkJUnitDependencies", CheckJUnitDependencies.class);

            project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> {
                task.dependsOn(checkJUnitDependencies);
            });

            // For backwards compatibility reasons, Gradle uses the legacy JUnit4 test toolchain for the default
            // test suite and the JUnit Platform test toolchain for everything else.
            // We want to use junit jupiter by default for the default test suite.
            testingExtension
                    .getSuites()
                    .named(
                            JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME,
                            JvmTestSuite.class,
                            JvmTestSuite::useJUnitJupiter);

            // Gradle <8 does not automatically add junit toolchain deps. When the test-sets plugin is used instead
            // of jvm-test-suites, these deps are not added either. We make sure that we lazily add these deps to
            // the correct configurations if required. Note: we can't lazily configure test tasks or test suite then
            // add the dependencies in that configuration action, as the Configurations may be resolved before the
            // tasks/suites are realised, hence we have to work from the Configurations directly.
            // See https://github.com/gradle/gradle/pull/26369
            project.getConfigurations().configureEach(configuration -> {
                configuration.getDependencies().addAllLater(project.provider(() -> {
                    return testSourceSetsWhereJunitToolchainDepsHaveNotBeenAutomaticallyAdded(project)
                            .flatMap(sourceSet -> {
                                if (configuration.getName().equals(sourceSet.getImplementationConfigurationName())) {
                                    return Stream.of(project.getDependencies().create(JUNIT_JUPITER));
                                }

                                if (configuration.getName().equals(sourceSet.getRuntimeOnlyConfigurationName())) {
                                    return Stream.of(project.getDependencies().create(JUNIT_PLATFORM_LAUNCHER));
                                }

                                return Stream.empty();
                            })
                            .findFirst()
                            .map(Set::of)
                            .orElseGet(Set::of);
                }));
            });

            project.getTasks().withType(Test.class).configureEach(task -> configureTestTask(testingExtension, task));
        });
    }

    private void configureTestTask(TestingExtension testingExtension, Test task) {
        // For test tasks not created using test suites (ie using the old unbroken dome test-suites plugin),
        // we must explicitly use the JUnit Platform
        if (testTaskNotCreatedByJvmTestSuites(testingExtension, task.getName())) {
            task.useJUnitPlatform();
        }

        task.systemProperty("junit.platform.output.capture.stdout", "true");
        task.systemProperty("junit.platform.output.capture.stderr", "true");

        // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
        task.systemProperty("junit.jupiter.execution.parallel.enabled", "true");

        // Computes the desired parallelism based on the number of available processors/cores
        task.systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic");

        // provide some stdout feedback when tests fail when running on CI and locally
        task.getTestLogging().getEvents().add(TestLogEvent.FAILED);

        // Only on CI and for non-unit test tasks, print out more detailed test information to avoid hitting the
        // circleci 10 min deadline if there are lots of tests. Don't do this locally to avoid spamming massive
        // amount of info for people running tests through the command line. Only for non unit test tasks as unit
        // test tasks tend to be fast and avoid this issue.
        if (!task.getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME) && "true".equals(System.getenv("CI"))) {
            task.getTestLogging()
                    .getEvents()
                    .addAll(Set.of(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED));
        }
    }

    private static Stream<SourceSet> testSourceSetsWhereJunitToolchainDepsHaveNotBeenAutomaticallyAdded(
            Project project) {

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        TestingExtension testingExtension = project.getExtensions().getByType(TestingExtension.class);

        // Gradle <8 does not automatically add junit toolchain deps - see https://github.com/gradle/gradle/pull/21919
        boolean gradleVersionLessThan8 = GradleVersion.current().compareTo(GRADLE_8) < 0;

        return project.getTasks().withType(Test.class).getNames().stream().flatMap(testTaskName -> {
            if (gradleVersionLessThan8 || testTaskNotCreatedByJvmTestSuites(testingExtension, testTaskName)) {
                return Stream.of(sourceSets.getByName(testTaskName));
            }

            return Stream.empty();
        });
    }

    private static boolean testTaskNotCreatedByJvmTestSuites(TestingExtension testingExtension, String testTaskName) {
        return !testingExtension.getSuites().getNames().contains(testTaskName);
    }
}
