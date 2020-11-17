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

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

public final class BaselineEnablePreviewFlag implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().withType(JavaCompile.class, t -> {
            List<String> args = t.getOptions().getCompilerArgs();
            args.add("--enable-preview"); // mutation is gross, but it's the gradle convention
        });
        project.getTasks().withType(Test.class, t -> {
            t.jvmArgs("--enable-preview");
        });
        project.getTasks().withType(JavaExec.class, t -> {
            t.jvmArgs("--enable-preview");
        });
    }
}
