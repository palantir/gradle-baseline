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

package com.palantir.baseline.tasks;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.palantir.baseline.plugins.BaselineTesting;
import com.palantir.baseline.util.VersionUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;

public class CheckJUnitDependencies extends DefaultTask {

    public CheckJUnitDependencies() {
        setGroup("Verification");
        setDescription("Ensures the correct JUnit4/5 dependencies are present, otherwise tests may silently not run");
        getOutputs().upToDateWhen(_task -> true);
    }

    @TaskAction
    public final void validateDependencies() {
        getProbablyTestSourceSets().forEach(ss -> {
            Optional<Test> maybeTestTask = BaselineTesting.getTestTaskForSourceSet(getProject(), ss);
            if (!maybeTestTask.isPresent()) {
                // source set doesn't have a test task, e.g. 'schema'
                return;
            }
            Test task = maybeTestTask.get();

            getProject().getLogger().info("Analyzing source set {} with task {}", ss.getName(), task.getName());
            validateSourceSet(ss, task);
        });
    }

    @Classpath
    public final Provider<List<Configuration>> getConfigurations() {
        return getProject().provider(() -> getProbablyTestSourceSets()
                .map(SourceSet::getRuntimeClasspathConfigurationName)
                .map(getProject().getConfigurations()::getByName)
                .collect(toList()));
    }

    private Stream<SourceSet> getProbablyTestSourceSets() {
        return getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets().stream()
                .filter(ss -> !ss.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME));
    }

    private void validateSourceSet(SourceSet ss, Test task) {
        Set<ResolvedComponentResult> deps = getProject()
                .getConfigurations()
                .getByName(ss.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .getResolutionResult()
                .getAllComponents();
        boolean junitJupiterIsPresent = hasDep(deps, CheckJUnitDependencies::isJunitJupiter);
        boolean vintageEngineExists = hasDep(deps, CheckJUnitDependencies::isVintageEngine);
        boolean spock1Dependency = hasDep(deps, CheckJUnitDependencies::isSpock1);
        boolean spock2OrGreaterDependency = hasDep(deps, CheckJUnitDependencies::isSpock2OrGreater);
        String testRuntimeOnly = ss.getRuntimeOnlyConfigurationName();
        boolean junitPlatformEnabled = BaselineTesting.useJUnitPlatformEnabled(task);

        // If some testing library happens to provide the junit-jupiter-api, then users might start using the
        // org.junit.jupiter.api.Test annotation, but as JUnit4 knows nothing about these, they'll silently not run
        // unless the user has wired up the dependency correctly.
        if (sourceSetMentionsJUnit5Api(ss)) {
            String runtime = ss.getRuntimeClasspathConfigurationName();
            Preconditions.checkState(
                    junitPlatformEnabled,
                    "Some tests mention JUnit5, but the '"
                            + task.getName()
                            + "' task does not have "
                            + "useJUnitPlatform() enabled. This means tests may be silently not running! Please "
                            + "add the following:\n\n"
                            + "    "
                            + runtime
                            + " 'org.junit.jupiter:junit-jupiter'\n");
        }

        // When doing an incremental migration to JUnit5, a project may have some JUnit4 and some JUnit5 tests at the
        // same time. It's crucial that they have the vintage engine set up correctly, otherwise tests may silently
        // not run!
        if (sourceSetMentionsJUnit4(ss)) {
            if (junitPlatformEnabled) { // people might manually enable this
                Preconditions.checkState(
                        junitJupiterIsPresent,
                        "Tests may be silently not running! Some tests still use JUnit4, but Gradle has "
                                + "been set to use JUnit Platform. "
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    "
                                + testRuntimeOnly
                                + " 'org.junit.jupiter:junit-jupiter'\n\n"
                                + "Otherwise they will silently not run.");

                Preconditions.checkState(
                        vintageEngineExists,
                        "Tests may be silently not running! Some tests still use JUnit4, but Gradle has "
                                + "been set to use JUnit Platform. "
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    "
                                + testRuntimeOnly
                                + " 'org.junit.vintage:junit-vintage-engine'\n\n"
                                + "Otherwise they will silently not run.");
            } else {
                Preconditions.checkState(
                        !junitJupiterIsPresent,
                        "Tests may be silently not running! Please remove "
                                + "'org.junit.jupiter:junit-jupiter' dependency "
                                + "because tests use JUnit4 and useJUnitPlatform() is not enabled.");
            }
        }

        if (sourceSetMentionsJqwikApi(ss)) {
            Preconditions.checkState(junitPlatformEnabled, "jqwik requires the junit platform");
            String runtime = ss.getRuntimeClasspathConfigurationName();
            Set<String> engines = BaselineTesting.getJUnitPlatformEngines(task);
            if (!engines.contains("jqwik")) {
                throw new IllegalStateException("Some tests mention jqwik, but the '"
                        + task.getName()
                        + "' task does not have the jqwik engine enabled. "
                        + "This means tests may be silently not running! Please "
                        + "add the following:\n\n"
                        + "test {\n"
                        + "    useJUnitPlatform {\n"
                        + "        includeEngines 'jqwik', 'junit-jupiter'\n"
                        + "    }\n"
                        + "}\n\nAs well as a dependency on the engine:\n\n"
                        + "    "
                        + runtime
                        + " 'net.jqwik:jqwik-engine'\n");
            }
            Preconditions.checkState(
                    hasDep(deps, CheckJUnitDependencies::isJqwikEngine),
                    "Some tests mention jqwik, but the '"
                            + task.getName()
                            + "' task does not depend on the jqwik engine. "
                            + "This means tests may be silently not running! Please add the following:\n\n"
                            + "    "
                            + runtime
                            + " 'net.jqwik:jqwik-engine'\n");
        }

        // spock uses JUnit4 under the hood, so the vintage engine is critical
        if (spock1Dependency && junitPlatformEnabled) {
            Preconditions.checkState(
                    vintageEngineExists,
                    "Tests may be silently not running! Spock 1.x dependency detected (which uses "
                            + "a JUnit4 Runner under the hood). Please add the following:\n\n"
                            + "    "
                            + testRuntimeOnly
                            + " 'org.junit.vintage:junit-vintage-engine'\n\n");
        }

        if (spock2OrGreaterDependency) {
            Preconditions.checkState(
                    junitPlatformEnabled,
                    "Tests may silently be not running! Spock 2.x dependency "
                            + "detected (which requires useJUnitPlatform() in order to run).");
        }
    }

    private boolean hasDep(Set<ResolvedComponentResult> deps, Predicate<ModuleVersionIdentifier> spec) {
        return deps.stream().anyMatch(component -> spec.test(component.getModuleVersion()));
    }

    private boolean sourceSetMentionsJUnit4(SourceSet ss) {
        // getAllJava() includes groovy sources too
        return !ss.getAllJava()
                .filter(file -> fileContainsSubstring(
                        file,
                        l -> l.contains("org.junit.Test")
                                || l.contains("org.junit.runner")
                                || l.contains("org.junit.ClassRule")))
                .isEmpty();
    }

    private boolean sourceSetMentionsJUnit5Api(SourceSet ss) {
        return !ss.getAllJava()
                .filter(file -> fileContainsSubstring(file, l -> l.contains("org.junit.jupiter.api.")))
                .isEmpty();
    }

    private boolean sourceSetMentionsJqwikApi(SourceSet ss) {
        return !ss.getAllJava()
                .filter(file -> fileContainsSubstring(file, l -> l.contains("net.jqwik.api.")))
                .isEmpty();
    }

    private boolean fileContainsSubstring(File file, Predicate<String> substring) {
        try (Stream<String> lines = Files.lines(file.toPath())) {
            return lines.anyMatch(substring);
        } catch (Exception e) {
            throw new RuntimeException("Unable to check file for junit dependencies: " + file, e);
        }
    }

    private static boolean isJunitJupiter(ModuleVersionIdentifier dep) {
        return "org.junit.jupiter".equals(dep.getGroup()) && "junit-jupiter".equals(dep.getName());
    }

    private static boolean isVintageEngine(ModuleVersionIdentifier dep) {
        return "org.junit.vintage".equals(dep.getGroup()) && "junit-vintage-engine".equals(dep.getName());
    }

    private static boolean isSpock1(ModuleVersionIdentifier dep) {
        return isSpock(dep) && VersionUtils.majorVersionNumber(dep.getVersion()) <= 1;
    }

    private static boolean isSpock2OrGreater(ModuleVersionIdentifier dep) {
        return isSpock(dep) && VersionUtils.majorVersionNumber(dep.getVersion()) >= 2;
    }

    private static boolean isSpock(ModuleVersionIdentifier dep) {
        return "org.spockframework".equals(dep.getGroup()) && "spock-core".equals(dep.getName());
    }

    private static boolean isJqwikEngine(ModuleVersionIdentifier dep) {
        return "net.jqwik".equals(dep.getGroup()) && "jqwik-engine".equals(dep.getName());
    }
}
