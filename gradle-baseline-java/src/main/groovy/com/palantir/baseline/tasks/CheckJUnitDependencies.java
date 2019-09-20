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

import com.google.common.base.Preconditions;
import com.palantir.baseline.plugins.BaselineTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;

public class CheckJUnitDependencies extends DefaultTask {

    public CheckJUnitDependencies() {
        setGroup("Verification");
        setDescription("Ensures the correct JUnit4/5 dependencies are present, otherwise tests may silently not run");
    }

    @TaskAction
    public final void validateDependencies() {
        getProject().getConvention()
                .getPlugin(JavaPluginConvention.class)
                .getSourceSets()
                .forEach(ss -> {
                    if (ss.getName().equals("main")) {
                        return;
                    }

                    Optional<Test> maybeTestTask = BaselineTesting.getTestTaskForSourceSet(getProject(), ss);
                    if (!maybeTestTask.isPresent()) {
                        // source set doesn't have a test task, e.g. 'schema'
                        return;
                    }
                    Test task = maybeTestTask.get();

                    getProject().getLogger().info(
                            "Analyzing source set {} with task {}",
                            ss.getName(), task.getName());
                    validateSourceSet(ss, task);
                });
    }

    private void validateSourceSet(SourceSet ss, Test task) {
        boolean junitJupiterIsPresent = hasDep(
                ss.getRuntimeClasspathConfigurationName(), CheckJUnitDependencies::isJunitJupiter);

        // If some testing library happens to provide the junit-jupiter-api, then users might start using the
        // org.junit.jupiter.api.Test annotation, but as JUnit4 knows nothing about these, they'll silently not run
        // unless the user has wired up the dependency correctly.
        if (sourceSetMentionsJUnit5Api(ss)) {
            String implementation = ss.getImplementationConfigurationName();
            Preconditions.checkState(
                    BaselineTesting.useJUnitPlatformEnabled(task),
                    "Some tests mention JUnit5, but the '" + task.getName() + "' task does not have "
                            + "useJUnitPlatform() enabled. This means tests may be silently not running! Please "
                            + "add the following:\n\n"
                            + "    " + implementation + " 'org.junit.jupiter:junit-jupiter'\n");
        }

        // When doing an incremental migration to JUnit5, a project may have some JUnit4 and some JUnit5 tests at the
        // same time. It's crucial that they have the vintage engine set up correctly, otherwise tests may silently
        // not run!
        if (sourceSetMentionsJUnit4(ss)) {
            if (BaselineTesting.useJUnitPlatformEnabled(task)) { // people might manually enable this
                String testRuntimeOnly = ss.getRuntimeConfigurationName() + "Only";
                Preconditions.checkState(
                        junitJupiterIsPresent,
                        "Tests may be silently not running! Some tests still use JUnit4, but Gradle has "
                                + "been set to use JUnit Platform. "
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    " + testRuntimeOnly + " 'org.junit.jupiter:junit-jupiter'\n\n"
                                + "Otherwise they will silently not run.");

                boolean vintageEngineExists = hasDep(
                        ss.getRuntimeClasspathConfigurationName(), CheckJUnitDependencies::isVintageEngine);
                Preconditions.checkState(
                        vintageEngineExists,
                        "Tests may be silently not running! Some tests still use JUnit4, but Gradle has "
                                + "been set to use JUnit Platform. "
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    " + testRuntimeOnly + " 'org.junit.vintage:junit-vintage-engine'\n\n"
                                + "Otherwise they will silently not run.");
            } else {
                Preconditions.checkState(
                        !junitJupiterIsPresent,
                        "Tests may be silently not running! Please remove "
                                + "'org.junit.jupiter:junit-jupiter' dependency "
                                + "because tests use JUnit4 and useJUnitPlatform() is not enabled.");
            }
        } else {
            String compileClasspath = ss.getCompileClasspathConfigurationName();
            boolean compilingAgainstOldJunit = hasDep(compileClasspath, CheckJUnitDependencies::isJunit4);
            Preconditions.checkState(
                    !compilingAgainstOldJunit,
                    "Extraneous dependency on JUnit4 (no test mentions JUnit4 classes). Please exclude "
                            + "this from compilation to ensure developers don't accidentally re-introduce it, e.g.\n\n"
                            + "    configurations." + compileClasspath + ".exclude module: 'junit'\n\n");
        }

        // sourcesets might also contain Spock classes, but we don't have any special validation for these.
    }

    private boolean hasDep(String configurationName, Predicate<ModuleVersionIdentifier> spec) {
        return getProject().getConfigurations()
                .getByName(configurationName)
                .getIncoming()
                .getResolutionResult()
                .getAllComponents()
                .stream()
                .anyMatch(component -> spec.test(component.getModuleVersion()));
    }

    private boolean sourceSetMentionsJUnit4(SourceSet ss) {
        return !ss.getAllSource()
                .filter(file -> fileContainsSubstring(file, l ->
                        l.contains("org.junit.Test")
                                || l.contains("org.junit.runner")
                                || l.contains("org.junit.ClassRule")))
                .isEmpty();
    }

    private boolean sourceSetMentionsJUnit5Api(SourceSet ss) {
        return !ss.getAllSource()
                .filter(file -> fileContainsSubstring(file, l -> l.contains("org.junit.jupiter.api.")))
                .isEmpty();
    }

    private boolean fileContainsSubstring(File file, Predicate<String> substring) {
        try (Stream<String> lines = Files.lines(file.toPath())) {
            boolean hit = lines.anyMatch(substring::test);
            getProject().getLogger().debug("[{}] {}", hit ? "hit" : "miss", file);
            return hit;
        } catch (IOException e) {
            throw new RuntimeException("Unable to check file " + file, e);
        }
    }

    private static boolean isJunitJupiter(ModuleVersionIdentifier dep) {
        return "org.junit.jupiter".equals(dep.getGroup()) && "junit-jupiter".equals(dep.getName());
    }

    private static boolean isVintageEngine(ModuleVersionIdentifier dep) {
        return "org.junit.vintage".equals(dep.getGroup()) && "junit-vintage-engine".equals(dep.getName());
    }

    private static boolean isJunit4(ModuleVersionIdentifier dep) {
        return "junit".equals(dep.getGroup()) && "junit".equals(dep.getName());
    }
}
