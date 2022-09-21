/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.compile.JavaCompile;

public final class BaselineNullAway implements Plugin<Project> {

    private static final Logger log = Logging.getLogger(BaselineNullAway.class);

    /** We may add a gradle extension in a future release allowing custom additional packages. */
    private static final ImmutableSet<String> DEFAULT_ANNOTATED_PACKAGES = ImmutableSet.of("com.palantir");

    private static final ImmutableList<String> NON_15_DEPS = ImmutableList.of(
            "com.uber.nullaway:nullaway:0.10.1",
            // align 'org.checkerframework' dependency versions on
            // the current latest version.
            "org.checkerframework:dataflow-errorprone:3.25.0",
            "org.checkerframework:dataflow-nullaway:3.25.0",
            "org.checkerframework:checker-qual:3.25.0");

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("com.palantir.baseline-error-prone", _unused0 -> {
            project.getPluginManager().withPlugin("java-base", _unused1 -> {
                applyToProject(project);
            });
        });
    }

    private void applyToProject(Project project) {
        String version = Optional.ofNullable(BaselineNullAway.class.getPackage().getImplementationVersion())
                .orElseGet(() -> {
                    log.warn("BaselineNullAway is using 'latest.release' - "
                            + "beware this compromises build reproducibility");
                    return "latest.release";
                });
        project.getConfigurations()
                .matching(new Spec<Configuration>() {
                    @Override
                    public boolean isSatisfiedBy(Configuration config) {
                        return "errorprone".equals(config.getName());
                    }
                })
                .configureEach(new Action<Configuration>() {
                    @Override
                    public void execute(Configuration _files) {
                        project.getDependencies()
                                .add("errorprone", "com.palantir.baseline:baseline-null-away:" + version);
                    }
                });
        configureErrorProneOptions(project, new Action<ErrorProneOptions>() {
            @Override
            public void execute(ErrorProneOptions options) {
                options.option("NullAway:AnnotatedPackages", String.join(",", DEFAULT_ANNOTATED_PACKAGES));
                // Relax some checks for test code
                if (options.getCompilingTestOnlyCode().get()) {
                    // NullAway has some poor interactions with mockito and
                    // tests generally do some odd accesses for brevity
                    options.disable("NullAway");
                }
            }
        });
        newerNullAwayInNonJdk15Projects(project);
    }

    private static void configureErrorProneOptions(Project proj, Action<ErrorProneOptions> action) {
        proj.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                project.getTasks().withType(JavaCompile.class).configureEach(new Action<JavaCompile>() {
                    @Override
                    public void execute(JavaCompile javaCompile) {
                        ((ExtensionAware) javaCompile.getOptions())
                                .getExtensions()
                                .configure(ErrorProneOptions.class, action);
                    }
                });
            }
        });
    }

    // Workaround for nullaway bugs in the last release to support jdk-15. Projects that don't use jdk-15
    // resolve a newer nullaway version with bug-fixes. This may be deleted after we've rolled everything
    // off jdk-15 MTS.
    private static void newerNullAwayInNonJdk15Projects(Project project) {
        project.getConfigurations()
                .matching(new Spec<Configuration>() {
                    @Override
                    public boolean isSatisfiedBy(Configuration config) {
                        return "errorprone".equals(config.getName());
                    }
                })
                .configureEach(new Action<Configuration>() {
                    @Override
                    public void execute(Configuration _files) {
                        for (String dep : NON_15_DEPS) {
                            project.getDependencies().addProvider("errorprone", project.provider(() -> {
                                // Cannot upgrade dependencies in this case because newer nullaway/checkerframework
                                // are not compatible with java 15, but fully support jdk11 and jdk17.
                                return anyProjectUsesJava15(project) ? null : dep;
                            }));
                        }
                    }
                });
    }

    private static boolean anyProjectUsesJava15(Project proj) {
        return proj.getAllprojects().stream()
                .anyMatch(project -> project.getTasks().withType(JavaCompile.class).stream()
                        .anyMatch(comp -> {
                            JavaVersion javaVersion = JavaVersion.toVersion(comp.getTargetCompatibility());
                            return javaVersion == JavaVersion.VERSION_15;
                        }));
    }
}
