/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.suppressibleerrorprone;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.compile.JavaCompile;

public abstract class SuppressibleErrorProneExtension {
    private final Project project;

    public SuppressibleErrorProneExtension(Project project) {
        this.project = project;
    }

    public abstract SetProperty<String> getPatchChecks();

    public abstract ListProperty<ConditionalPatchCheck> getConditionalPatchChecks();

    public final Set<String> patchChecksForCompilation(JavaCompile javaCompile) {
        return Stream.concat(
                        getPatchChecks().get().stream(),
                        getConditionalPatchChecks().get().stream()
                                .filter(conditionalPatchCheck ->
                                        conditionalPatchCheck.when().isSatisfiedBy(javaCompile))
                                .flatMap(conditionalPatchCheck -> conditionalPatchCheck.checks().stream()))
                .collect(Collectors.toSet());
    }

    public final void configureEachErrorProneOptions(Action<ErrorProneOptions> action) {
        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> ((ExtensionAware)
                        javaCompile.getOptions())
                .getExtensions()
                .configure(ErrorProneOptions.class, action));
    }
}
