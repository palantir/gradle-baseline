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
import org.gradle.api.provider.SetProperty;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Extension to configure {@code --add-exports [VALUE]=ALL-UNNAMED} for the current module.
 */
public class BaselineExportsExtension {

    private final SetProperty<String> exports;

    @Inject
    public BaselineExportsExtension(Project project) {
        exports = project.getObjects().setProperty(String.class);
    }

    /** Target {@link JavaLanguageVersion} for compilation. */
    public final SetProperty<String> exports() {
        return exports;
    }
}
