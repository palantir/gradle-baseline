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
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineTest {

    @BeforeEach
    void setup(RootProject rootProject, SubProject subProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline");
    }

    @Test
    void applies_to_root_project_and_subprojects(GradleInvoker gradle, RootProject rootProject, SubProject subProject) {
        // Check plugin application during afterEvaluate to ensure all plugins are applied
        rootProject.buildGradle().append("""
            afterEvaluate {
                println "ROOT_HAS_CIRCLECI: ${pluginManager.hasPlugin('com.palantir.baseline-circleci')}"
                println "ROOT_HAS_CONFIG: ${pluginManager.hasPlugin('com.palantir.baseline-config')}"
                println "ROOT_HAS_CHECKSTYLE: ${pluginManager.hasPlugin('com.palantir.baseline-checkstyle')}"
                println "ROOT_HAS_ERROR_PRONE: ${pluginManager.hasPlugin('com.palantir.baseline-error-prone')}"
                println "ROOT_HAS_IDEA: ${pluginManager.hasPlugin('com.palantir.baseline-idea')}"
                println "ROOT_HAS_CLASS_UNIQUENESS: ${pluginManager.hasPlugin('com.palantir.baseline-class-uniqueness')}"
            }
            """);

        subProject.buildGradle().append("""
            afterEvaluate {
                println "SUB_HAS_CHECKSTYLE: ${pluginManager.hasPlugin('com.palantir.baseline-checkstyle')}"
                println "SUB_HAS_ERROR_PRONE: ${pluginManager.hasPlugin('com.palantir.baseline-error-prone')}"
                println "SUB_HAS_IDEA: ${pluginManager.hasPlugin('com.palantir.baseline-idea')}"
                println "SUB_HAS_CLASS_UNIQUENESS: ${pluginManager.hasPlugin('com.palantir.baseline-class-uniqueness')}"
            }
            """);

        InvocationResult result = gradle.withArgs("help").buildsSuccessfully();

        // Check root project has all expected plugins and subproject has all expected plugins
        assertThat(result)
                .output()
                .contains("ROOT_HAS_CIRCLECI: true")
                .contains("ROOT_HAS_CONFIG: true")
                .contains("ROOT_HAS_CHECKSTYLE: true")
                // TEMPHACK(okelvin): remove this to decouple baseline-error-prone
                .contains("ROOT_HAS_ERROR_PRONE: true")
                .contains("ROOT_HAS_IDEA: true")
                .contains("ROOT_HAS_CLASS_UNIQUENESS: true")
                .contains("SUB_HAS_CHECKSTYLE: true")
                // TEMPHACK(okelvin): remove this to decouple baseline-error-prone
                .contains("SUB_HAS_ERROR_PRONE: true")
                .contains("SUB_HAS_IDEA: true")
                .contains("SUB_HAS_CLASS_UNIQUENESS: true");
    }
}
