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
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineTesting implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(BaselineTesting.class);

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CrashOnOutOfMemoryError");

            if (!Objects.equals("true", project.findProperty("com.palantir.baseline.restore-test-cache"))) {
                // Never cache test tasks, until we work out the correct inputs for ETE / integration tests
                task.getOutputs().cacheIf(t -> false);
            }
        });

        project.getPlugins().withType(JavaPlugin.class, unusedPlugin -> {
            // afterEvaluate necessary because the junit-jupiter dep might be added further down the build.gradle
            project.afterEvaluate(proj -> {
                proj.getConvention()
                        .getPlugin(JavaPluginConvention.class)
                        .getSourceSets()
                        .matching(ss -> hasCompileDependenciesMatching(proj, ss, BaselineTesting::isJunitJupiter))
                        .forEach(ss -> {
                            Optional<Test> maybeTestTask = getTestTaskForSourceSet(proj, ss);
                            if (!maybeTestTask.isPresent()) {
                                log.warn("Detected 'org:junit.jupiter:junit-jupiter', but unable to find test task");
                                return;
                            }
                            log.info(
                                    "Detected 'org:junit.jupiter:junit-jupiter', enabling useJUnitPlatform() on {}",
                                    maybeTestTask.get().getName());
                            enableJunit5ForTestTask(maybeTestTask.get());
                        });
            });

            TaskProvider<CheckJUnitDependencies> task =
                    project.getTasks().register("checkJUnitDependencies", CheckJUnitDependencies.class);

            project.getTasks().findByName(JavaPlugin.TEST_TASK_NAME).dependsOn(task);
        });
    }

    public static Optional<Test> getTestTaskForSourceSet(Project proj, SourceSet ss) {
        String testTaskName = ss.getTaskName(null, "test");

        Task task1 = proj.getTasks().findByName(testTaskName);
        if (task1 instanceof Test) {
            return Optional.of((Test) task1);
        }

        // unbroken dome does this
        Task task2 = proj.getTasks().findByName(ss.getName());
        if (task2 instanceof Test) {
            return Optional.of((Test) task2);
        }
        return Optional.empty();
    }

    private static boolean hasCompileDependenciesMatching(Project project, SourceSet sourceSet, Spec<Dependency> spec) {
        return project
                .getConfigurations()
                .getByName(sourceSet.getCompileClasspathConfigurationName())
                .getAllDependencies()
                .matching(spec)
                .stream()
                .findAny()
                .isPresent();
    }

    private static boolean isJunitJupiter(Dependency dep) {
        return Objects.equals(dep.getGroup(), "org.junit.jupiter")
                && dep.getName().equals("junit-jupiter");
    }

    private static void enableJunit5ForTestTask(Test task) {
        if (!useJUnitPlatformEnabled(task)) {
            task.useJUnitPlatform();
        }

        task.systemProperty("junit.platform.output.capture.stdout", "true");
        task.systemProperty("junit.platform.output.capture.stderr", "true");

        // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
        task.systemProperty("junit.jupiter.execution.parallel.enabled", "true");

        // Computes the desired parallelism based on the number of available processors/cores
        task.systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic");

        // provide some stdout feedback when tests fail
        task.testLogging(testLogging -> testLogging.events("failed"));
    }

    public static boolean useJUnitPlatformEnabled(Test task) {
        return task.getOptions() instanceof JUnitPlatformOptions;
    }
}
