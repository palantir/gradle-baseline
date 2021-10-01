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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.palantir.baseline.tasks.CheckJUnitDependencies;
import com.palantir.baseline.tasks.CheckUnusedDependenciesTask;
import com.palantir.baseline.util.VersionUtils;
import java.util.Objects;
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
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

            // repos that use 'snapshot' style testing should all use one convenient task to refresh the snapshots,
            // ./gradlew test -Drecreate=true
            boolean shouldRecreate = Boolean.getBoolean("recreate");
            task.systemProperty("recreate", Boolean.toString(shouldRecreate));
            if (shouldRecreate) {
                task.getOutputs().upToDateWhen(t -> false);
            }
        });

        project.getPlugins().withType(JavaPlugin.class, unusedPlugin -> {
            TaskProvider<CheckJUnitDependencies> checkJUnitDependencies =
                    project.getTasks().register("checkJUnitDependencies", CheckJUnitDependencies.class);

            project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets()
                    .configureEach(sourceSet -> {
                        getTestTaskForSourceSet(project, sourceSet).ifPresent(testTask -> {
                            testTask.dependsOn(checkJUnitDependencies);
                        });

                        ifHasResolvedCompileDependenciesMatching(
                                project,
                                sourceSet,
                                BaselineTesting::requiresJunitPlatform,
                                () -> fixSourceSet(project, sourceSet));
                    });
        });
    }

    private void fixSourceSet(Project project, SourceSet ss) {
        Optional<Test> maybeTestTask = getTestTaskForSourceSet(project, ss);
        if (!maybeTestTask.isPresent()) {
            log.warn("Detected 'org:junit.jupiter:junit-jupiter', but unable to find test task");
            return;
        }
        log.info(
                "Detected 'org:junit.jupiter:junit-jupiter', enabling useJUnitPlatform() on {}",
                maybeTestTask.get().getName());
        enableJunit5ForTestTask(maybeTestTask.get());

        // Also wire up a test ignore for this source set
        project.getPlugins().withType(BaselineExactDependencies.class, exactDeps -> {
            TaskContainer tasks = project.getTasks();
            tasks.named(
                    BaselineExactDependencies.checkUnusedDependenciesNameForSourceSet(ss),
                    CheckUnusedDependenciesTask.class,
                    task -> task.ignore("org.junit.jupiter", "junit-jupiter"));
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

    private static void ifHasResolvedCompileDependenciesMatching(
            Project project, SourceSet sourceSet, Predicate<ModuleComponentIdentifier> spec, Runnable runnable) {
        project.getConfigurations()
                .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .afterResolve(deps -> {
                    boolean anyMatch = deps.getResolutionResult().getAllComponents().stream()
                            .map(ResolvedComponentResult::getId)
                            .filter(componentId -> componentId instanceof ModuleComponentIdentifier)
                            .map(componentId -> (ModuleComponentIdentifier) componentId)
                            .anyMatch(spec);

                    if (anyMatch) {
                        runnable.run();
                    }
                });
    }

    private static boolean requiresJunitPlatform(ModuleComponentIdentifier dep) {
        return isDep(dep, "org.junit.jupiter", "junit-jupiter")
                || (isDep(dep, "org.spockframework", "spock-core")
                        && VersionUtils.majorVersionNumber(dep.getVersion()) >= 2);
    }

    private static boolean isDep(ModuleComponentIdentifier dep, String group, String name) {
        return group.equals(dep.getGroup()) && name.equals(dep.getModule());
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

        // provide some stdout feedback when tests fail when running on CI and locally
        task.getTestLogging().getEvents().add(TestLogEvent.FAILED);

        // Only on CI and for non-unit test tasks, print out more detailed test information to avoid hitting the
        // circleci 10 min deadline if there are lots of tests. Don't do this locally to avoid spamming massive
        // amount of info for people running tests through the command line. Only for non unit test tasks as unit
        // test tasks tend to be fast and avoid this issue.
        if (!task.getName().equals("test") && "true".equals(System.getenv("CI"))) {
            task.getTestLogging()
                    .getEvents()
                    .addAll(ImmutableSet.of(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED));
        }
    }

    public static boolean useJUnitPlatformEnabled(Test task) {
        return task.getOptions() instanceof JUnitPlatformOptions;
    }
}
