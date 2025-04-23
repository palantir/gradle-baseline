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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class TargetJavaVersion extends DefaultTask {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Input
    public abstract Property<IsLibraryWithReason> getIsLibraryWithReason();

    @Input
    public abstract Property<ChosenJavaVersion> getTarget();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public final void action() throws IOException {
        OBJECT_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        getOutputFile().get().getAsFile(),
                        new MaxJavaVersionJson(
                                getIsLibraryWithReason().get(), getTarget().get()));
    }

    record MaxJavaVersionJson(IsLibraryWithReason isLibraryWithReason, ChosenJavaVersion target) {}
}
