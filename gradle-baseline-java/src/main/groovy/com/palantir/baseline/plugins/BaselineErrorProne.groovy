/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins

import net.ltgt.gradle.errorprone.ErrorPronePlugin
import org.gradle.api.GradleException
import org.gradle.api.Project

class BaselineErrorProne extends AbstractBaselinePlugin {

    void apply(Project project) {
        this.project = project

        project.plugins.apply(ErrorPronePlugin)
        project.dependencies {
            // TODO(rfink): This is somewhat ugly. Is there a better to add the processor dependency on the library?
            errorprone "com.palantir.baseline:baseline-error-prone:${extractVersionString()}"
        }
    }

    private String extractVersionString() {
        return this.getClass().getResource("/baseline-version.txt").text
    }
}
