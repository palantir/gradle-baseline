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

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Action;
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
            }
        });
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
}
