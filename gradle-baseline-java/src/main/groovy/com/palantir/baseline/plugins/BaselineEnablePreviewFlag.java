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

import com.palantir.baseline.plugins.javaversions.BaselineJavaVersion;
import java.util.Collections;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineEnablePreviewFlag implements Plugin<Project> {

    private static final String FLAG = "--enable-preview";

    // Yes truly javadoc wants a single leading dash, other javac wants a double leading dash.
    // We also have to use these manual string options because they don't have first-class methods
    // yet (e.g. https://github.com/gradle/gradle/issues/12898)
    private static final String JAVADOC_FLAG = "-enable-preview";

    /** This plugin assumes users already apply the {@link BaselineJavaVersion}. */
    @Override
    public void apply(Project project) {

        project.getPlugins().withId("java", _unused -> {
            project.getTasks().withType(JavaCompile.class).configureEach(t -> {
                List<CommandLineArgumentProvider> args = t.getOptions().getCompilerArgumentProviders();
                args.add(new EnablePreview()); // mutation is gross, but it's the gradle convention
            });
            project.getTasks().withType(Test.class).configureEach(t -> {
                t.getJvmArgumentProviders().add(new EnablePreview());
            });
            project.getTasks().withType(JavaExec.class).configureEach(t -> {
                t.getJvmArgumentProviders().add(new EnablePreview());
            });

            // sadly we have to use afterEvaluate because the Javadoc task doesn't support passing in providers
            project.afterEvaluate(_unused2 -> {
                project.getTasks().withType(Javadoc.class).configureEach(t -> {
                    CoreJavadocOptions options = (CoreJavadocOptions) t.getOptions();
                    options.addBooleanOption(JAVADOC_FLAG, true);
                });
            });
        });
    }

    // TODO(dfox): throw this away and just use a list of command line arg strings
    private static class EnablePreview implements CommandLineArgumentProvider {

        @Override
        public Iterable<String> asArguments() {
            return Collections.singletonList(FLAG);
        }
    }

    public static boolean shouldEnablePreview(Project project) {
        return project.getPlugins().hasPlugin(BaselineEnablePreviewFlag.class);
    }
}
