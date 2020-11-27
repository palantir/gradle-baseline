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

package com.palantir.baseline.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * By default, Gradle will infer sourceCompat based on whatever JVM is currently being used to evaluate the
 * build.gradle files. This is bad for reproducibility because if we make an automated PR to upgrade the Java major
 * version (e.g. 11 -> 15) then a library might unintentionally start publishing jars containing Java15 bytecode!
 *
 * Better to just require everyone to specify sourceCompatibility explicitly!
 */
public class CheckExplicitSourceCompatibilityTask extends DefaultTask {

    private final Property<Boolean> shouldFix;

    @Inject
    public CheckExplicitSourceCompatibilityTask(ObjectFactory objectFactory) {
        setGroup("Verification");
        setDescription("Ensures build.gradle specifies sourceCompatibility explicitly, otherwise it is inferred based"
                + " on $JAVA_HOME which is fragile.");
        this.shouldFix = objectFactory.property(Boolean.class);
        this.shouldFix.set(false);
    }

    @Option(option = "fix", description = "Whether to apply the suggested fix to build.gradle")
    public void setShouldFix(boolean value) {
        shouldFix.set(value);
    }

    @TaskAction
    public void taskAction() throws IOException {
        org.gradle.api.plugins.internal.DefaultJavaPluginConvention convention =
                (org.gradle.api.plugins.internal.DefaultJavaPluginConvention)
                        getProject().getConvention().getPlugin(JavaPluginConvention.class);

        if (convention.getRawSourceCompatibility() != null) {
            // this means the user has explicitly set sourceCompatibility, which is great.
            return;
        }

        if (shouldFix.get()) {
            Files.write(
                    getProject().getBuildFile().toPath(),
                    Collections.singletonList(String.format("%nsourceCompatibility = %s%n", JavaVersion.current())),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
            return;
        }

        throw new GradleException(String.format(
                "Project must set java 'sourceCompatibility' explicitly in %s, "
                        + "otherwise compilation will not be reproducible but instead depends on the Java version "
                        + "that Gradle is currently running with (%s). Re-run ./gradlew %s --fix to automatically add "
                        + "a suggested line (you may need to adjust the number, e.g. to '8' for maximum "
                        + "compatibility).",
                getProject().getBuildFile(), JavaVersion.current(), getPath()));
    }
}
