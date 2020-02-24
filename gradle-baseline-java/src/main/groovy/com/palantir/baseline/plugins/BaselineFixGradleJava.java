/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Fixes up java configurations that are left resolvable and consumable for legacy reasons, in order to prepare for
 * Gradle 7 and to showcase code that resolves these when it shouldn't.
 *
 * <p>This will probably break some plugins in the wild, but we'd rather deal with that now than later.
 */
public final class BaselineFixGradleJava implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java-base", javaPlugin -> {
            SourceSetContainer sourceSets = project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets();

            sourceSets.configureEach(sourceSet -> {
                fixLegacyJavaConfigurationsForSourceSet(project.getConfigurations(), sourceSet);
            });
        });
    }

    /**
     * Fixes up the legacy java configurations that should be set as {@code !canBeConsumed} and {@code !canBeResolved}.
     * Note: configurations are set up in {@link JavaBasePlugin#defineConfigurationsForSourceSet}.
     */
    private void fixLegacyJavaConfigurationsForSourceSet(ConfigurationContainer configurations, SourceSet sourceSet) {
        Stream.of(
                        sourceSet.getCompileOnlyConfigurationName(),
                        sourceSet.getCompileConfigurationName(),
                        sourceSet.getRuntimeConfigurationName())
                .forEach(confName -> configurations.named(confName, conf -> {
                    conf.setCanBeConsumed(false);
                    conf.setCanBeResolved(false);
                }));
    }
}
