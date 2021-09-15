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

package com.palantir.baseline.extensions;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public class BaselineJavaVersionExtension {

    private final Property<JavaLanguageVersion> target;
    private final Property<JavaLanguageVersion> runtime;

    @Inject
    public BaselineJavaVersionExtension(Project project) {
        target = project.getObjects().property(JavaLanguageVersion.class);
        runtime = project.getObjects().property(JavaLanguageVersion.class);
        // Runtime defaults to the target value
        runtime.set(target);
    }

    /** Target {@link JavaLanguageVersion} for compilation. */
    public final Property<JavaLanguageVersion> target() {
        return target;
    }

    public final void setTarget(JavaLanguageVersion value) {
        target.set(value);
    }

    /** Runtime {@link JavaLanguageVersion} for testing and distributions. */
    public final Property<JavaLanguageVersion> runtime() {
        return runtime;
    }

    public final void setRuntime(JavaLanguageVersion value) {
        runtime.set(value);
    }
}
