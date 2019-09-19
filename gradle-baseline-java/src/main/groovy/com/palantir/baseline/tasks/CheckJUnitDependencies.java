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
import java.util.Optional;
import java.util.function.Predicate;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
                        return;
                    }
                    Test task = maybeTestTask.get();

                    getProject().getLogger().lifecycle("Analyzing source set {} with task {}",
                            ss.getName(), task.getName());
                    validateSourceSet(ss, task);
                });
    }

    private void validateSourceSet(SourceSet ss, Test task) {
        boolean junitJupiterIsPresent = hasRuntimeDepMatching(ss, BaselineTesting::isJunitJupiter);

        // When doing an incremental migration to JUnit5, a project may have some JUnit4 and some JUnit5 tests at the
        // same time. It's crucial that they have the vintage engine set up correctly, otherwise tests may silently
        // not run!
        if (sourceSetMentionsJUnit4(ss)) {
            if (BaselineTesting.useJUnitPlatformEnabled(task)) {
                Preconditions.checkState(junitJupiterIsPresent,
                        "Some tests still use JUnit4, but Gradle has been set to use JUnit Platform."
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    runtimeOnly 'org.junit.jupiter:junit-jupiter'\n\n"
                                + "Otherwise they will silently not run.");

                boolean vintageEngineExists = hasRuntimeDepMatching(ss, BaselineTesting::isVintageEngine);
                Preconditions.checkState(vintageEngineExists,
                        "Some tests still use JUnit4, but Gradle has been set to use JUnit Platform."
                                + "To ensure your old JUnit4 tests still run, please add the following:\n\n"
                                + "    runtimeOnly 'org.junit.vintage:junit-vintage-engine'\n\n"
                                + "Otherwise they will silently not run.");
            } else {
                Preconditions.checkState(!junitJupiterIsPresent,
                        "Please remove 'org.junit.jupiter:junit-jupiter' from runtimeClasspath "
                                + "because tests use JUnit4 and useJUnitPlatform() is not enabled.");

                // we're confident they have 'junit:junit' on the classpath already in order to compile
            }
        }

        // If some testing library happens to provide the junit-jupiter-api, then users might start using the
        // org.junit.jupiter.api.Test annotation, but as JUnit4 knows nothing about these, they'll silently not run
        // unless the user has wired up the dependency correctly.
        if (sourceSetMentionsJUnit5Api(ss)) {
            if (BaselineTesting.useJUnitPlatformEnabled(task)) {
                Preconditions.checkState(BaselineTesting.useJUnitPlatformEnabled(task), "TODO");
                Preconditions.checkState(junitJupiterIsPresent, "TODO");
            } else {
                throw new GradleException("TODO");
            }
        }

        // sourcesets might also contain Spock classes, but we don't have any special validation for these.
    }

    private boolean hasRuntimeDepMatching(SourceSet ss, Predicate<ModuleVersionIdentifier> spec) {
        return getProject().getConfigurations()
                .getByName(ss.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .getResolutionResult()
                .getAllComponents()
                .stream()
                .anyMatch(component -> spec.test(component.getModuleVersion()));
    }

    private static boolean sourceSetMentionsJUnit4(SourceSet ss) {
        // TODO(dfox): implement this
        return false;
    }

    private static boolean sourceSetMentionsJUnit5Api(SourceSet ss) {
        // TODO(dfox): implement this
        return false;
    }
}
