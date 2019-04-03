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

package com.palantir.baseline.plugins.extensions;

import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

public class BaselineClassUniquenessExtension {
    private final ListProperty<String> configurations;

    public BaselineClassUniquenessExtension(Project project) {
        configurations = project.getObjects().listProperty(String.class);
    }

    public final Provider<List<String>> getConfigurations() {
        return configurations;
    }

    public final void setConfigurations(Iterable<String> confs) {
        configurations.set(confs);
    }
}
