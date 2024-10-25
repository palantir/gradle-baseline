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

import java.util.Objects;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;

public final class IfModuleIsUsed implements Spec<JavaCompile> {
    private final String group;
    private final String module;

    public IfModuleIsUsed(String group, String module) {
        this.group = group;
        this.module = module;
    }

    @Override
    public boolean isSatisfiedBy(JavaCompile javaCompile) {
        Project project = javaCompile.getProject();
        return project
                .getExtensions()
                .getByType(SourceSetContainer.class)
                .matching(sourceSet -> sourceSet.getCompileJavaTaskName().equals(javaCompile.getName()))
                .stream()
                .findFirst()
                .filter(sourceSet -> {
                    Configuration compileClasspath =
                            project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
                    return hasDependenciesMatching(
                            compileClasspath,
                            mci -> Objects.equals(mci.getGroup(), group) && Objects.equals(mci.getModule(), module));
                })
                .isPresent();
    }

    private static boolean hasDependenciesMatching(Configuration configuration, Spec<ModuleComponentIdentifier> spec) {
        return configuration
                .getIncoming()
                .artifactView(viewConfiguration -> viewConfiguration.componentFilter(ci ->
                        ci instanceof ModuleComponentIdentifier && spec.isSatisfiedBy((ModuleComponentIdentifier) ci)))
                .getArtifacts()
                .iterator()
                .hasNext();
    }
}
