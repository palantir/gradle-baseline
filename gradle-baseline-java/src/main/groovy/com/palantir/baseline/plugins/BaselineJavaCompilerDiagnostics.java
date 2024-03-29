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

package com.palantir.baseline.plugins;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Applies the {@code -Xmaxwarns} and {@code -Xmaxerrs} compiler options with a very large
 * limit to avoid truncating failure info.
 */
public final class BaselineJavaCompilerDiagnostics implements Plugin<Project> {

    // Default maxwarns/maxerrs is only 100, which often makes it difficult to debug
    // annotation processing issues.
    private static final String MANY = "10000";

    private static final String MAX_WARNINGS_ARG = "-Xmaxwarns";
    private static final String MAX_ERRORS_ARG = "-Xmaxerrs";

    @Override
    public void apply(Project proj) {
        proj.afterEvaluate(
                project -> project.getTasks().withType(JavaCompile.class).configureEach(javaCompileTask -> {
                    List<String> compilerArgs = javaCompileTask.getOptions().getCompilerArgs();
                    // Avoid overriding options that have already been set
                    if (!compilerArgs.contains(MAX_WARNINGS_ARG)) {
                        compilerArgs.add(MAX_WARNINGS_ARG);
                        compilerArgs.add(MANY);
                    }
                    if (!compilerArgs.contains(MAX_ERRORS_ARG)) {
                        compilerArgs.add(MAX_ERRORS_ARG);
                        compilerArgs.add(MANY);
                    }
                }));
    }
}
