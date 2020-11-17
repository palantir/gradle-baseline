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

import java.util.Collections;
import java.util.List;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineEnablePreviewFlag implements Plugin<Project> {

    private static final String FLAG = "--enable-preview";

    @Override
    public void apply(Project project) {
        // The idea behind registering a single 'extra property' is that other plugins (like
        // sls-packaging) can easily detect this and also also add the --enable-preview jvm arg
        Provider<Boolean> enablePreview = project.provider(() -> {
            JavaVersion jvmExecutingGradle = JavaVersion.current();
            JavaPluginConvention javaConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
            if (javaConvention == null) {
                return false;
            }

            return javaConvention.getSourceCompatibility() == jvmExecutingGradle;
        });

        project.getExtensions().getExtraProperties().set("enablePreview", enablePreview);

        project.getTasks().withType(JavaCompile.class, t -> {
            List<CommandLineArgumentProvider> args = t.getOptions().getCompilerArgumentProviders();
            args.add(new MaybeEnablePreview(enablePreview)); // mutation is gross, but it's the gradle convention
        });
        project.getTasks().withType(Test.class, t -> {
            t.getJvmArgumentProviders().add(new MaybeEnablePreview(enablePreview));
        });
        project.getTasks().withType(JavaExec.class, t -> {
            t.getJvmArgumentProviders().add(new MaybeEnablePreview(enablePreview));
        });
    }

    private static class MaybeEnablePreview implements CommandLineArgumentProvider {
        private final Provider<Boolean> shouldEnable;

        MaybeEnablePreview(Provider<Boolean> shouldEnable) {
            this.shouldEnable = shouldEnable;
        }

        @Override
        public Iterable<String> asArguments() {
            return shouldEnable.get() ? Collections.singletonList(FLAG) : Collections.emptyList();
        }
    }
}
