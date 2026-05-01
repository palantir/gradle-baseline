/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineIntegrationTest {

    GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline");
        return rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            """);
    }

    @Test
    void can_apply_on_gradle(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        SubProject javaProject = rootProject.subproject("java-project");
        javaProject.buildGradle().plugins().add("java");
        rootProject.subproject("other-project");

        gradle.withArgs("-s").buildsSuccessfully();
    }
}
