/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineIdeaTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("idea").add("com.palantir.baseline-idea");
    }

    @Test
    void baseline_idea_plugin_applied(GradleInvoker gradle) {
        assertThat(gradle.withArgs("tasks", "--all").buildsSuccessfully())
                .output()
                .contains("idea")
                .contains("ideaModule")
                .contains("ideaProject");
    }
}
