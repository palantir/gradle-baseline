/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import java.util.Optional;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.compile.JavaCompile;

public final class BaselineNullAway implements Plugin<Project> {

    private static final Logger log = Logging.getLogger(BaselineNullAway.class);

    // This nullaway dependency in our plugin allows dependency upgrades on the baseline
    // project to ensure nullaway remains up to date for all consumers.
    private static final String NULLAWAY_VERSION = "0.10.1";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("com.palantir.baseline-error-prone", _unused0 -> {
            project.getPluginManager().withPlugin("java", _unused1 -> {
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
        project.getDependencies().add("errorprone", "com.palantir.baseline:baseline-null-away:" + version);
        configureErrorProneOptions(project, new Action<ErrorProneOptions>() {
            @Override
            public void execute(ErrorProneOptions options) {
                options.option("NullAway:AnnotatedPackages", "com.palantir");
                options.option("NullAway:CheckOptionalEmptiness", "true");
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
