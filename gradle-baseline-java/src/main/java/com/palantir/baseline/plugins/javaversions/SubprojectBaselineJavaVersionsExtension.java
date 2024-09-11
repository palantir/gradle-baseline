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

package com.palantir.baseline.plugins.javaversions;

import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

public class SubprojectBaselineJavaVersionsExtension implements BaselineJavaVersionsExtensionSetters {
    private final Project project;

    @Inject
    public SubprojectBaselineJavaVersionsExtension(Project project) {
        this.project = project;
    }

    @Override
    public final void setLibraryTarget(int _value) {
        throw throwCannotSetFromSubproject();
    }

    @Override
    public final void setLibraryTarget(String value) {
        throw throwCannotSetFromSubproject();
    }

    @Override
    public final void setDistributionTarget(int _value) {
        throw throwCannotSetFromSubproject();
    }

    @Override
    public final void setDistributionTarget(String _value) {
        throw throwCannotSetFromSubproject();
    }

    @Override
    public final void setRuntime(int _value) {
        throw throwCannotSetFromSubproject();
    }

    @Override
    public final void setRuntime(String _value) {
        throw throwCannotSetFromSubproject();
    }

    private RuntimeException throwCannotSetFromSubproject() {
        throw new GradleException("The javaVersions extension can only be used from the root project."
                + " Did you mean javaVersion, which can be used to override on a project-by-project basis?"
                + " You used it from "
                + project.getName());
    }
}
