/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Not opting into build caching; explicit opt-out is required by Gradle 9.7")
public abstract class ExplainJavaVersions extends DefaultTask {
    @Input
    public abstract Property<ChosenJavaVersion> getTarget();

    @Input
    public abstract Property<ChosenJavaVersion> getDefaultTarget();

    @Input
    public abstract Property<ChosenJavaVersion> getRuntime();

    @Input
    public abstract Property<ChosenJavaVersion> getDefaultRuntime();

    @Input
    public abstract Property<String> getReasoning();

    @TaskAction
    public final void action() {
        getLogger()
                .lifecycle(
                        "target  = {} {}",
                        getTarget().get().toString(),
                        defaultValueChanged(getTarget(), getDefaultTarget()));

        getLogger()
                .lifecycle(
                        "runtime = {} {}",
                        getRuntime().get().toString(),
                        defaultValueChanged(getRuntime(), getDefaultRuntime()));

        getLogger().lifecycle("Reason: {}", getReasoning().get());
    }

    private static String defaultValueChanged(
            Property<ChosenJavaVersion> actualValue, Property<ChosenJavaVersion> defaultValue) {
        return actualValue.get().equals(defaultValue.get())
                ? "(default value)"
                : String.format("(default value was %s - changed by a Gradle script or plugin)", defaultValue.get());
    }
}
