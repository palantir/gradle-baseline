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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineFormatTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-format");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            """);
    }

    @Test
    void spotless_plugin_applied(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            afterEvaluate {
                println "HAS_SPOTLESS: ${pluginManager.hasPlugin('com.diffplug.spotless')}"
            }
            """);

        InvocationResult result = gradle.withArgs("help").buildsSuccessfully();

        assertThat(result)
                .output()
                .as("baseline-format applies the spotless plugin as a side effect")
                .contains("HAS_SPOTLESS: true");
    }

    @Test
    void baseline_format_creates_format_task(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            afterEvaluate {
                println "HAS_FORMAT_TASK: ${tasks.findByName('format') != null}"
            }
            """);

        InvocationResult result = gradle.withArgs("help").buildsSuccessfully();

        assertThat(result)
                .output()
                .as("baseline-format registers a 'format' task")
                .contains("HAS_FORMAT_TASK: true");
    }

    @Test
    void spotless_plugin_eager_creation_issue(GradleInvoker gradle, RootProject rootProject) {
        // Register a task whose configuration block throws — if spotless eagerly configured
        // all tasks (https://github.com/diffplug/spotless/issues/444), this would trigger the throw.
        rootProject.buildGradle().append("""
            tasks.register("foo") {
                throw new RuntimeException("See https://github.com/diffplug/spotless/issues/444")
            }
            """);

        gradle.withArgs("help").buildsSuccessfully();
    }
}
