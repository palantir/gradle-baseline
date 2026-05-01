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
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("verification tasks inspect the project's task graph at execution time")
class BaselineCheckstyleTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-checkstyle");
    }

    @Test
    void baseline_checkstyle_plugin_applied(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            tasks.register('verifyPlugin') {
                doLast {
                    println "HAS_BASELINE_CHECKSTYLE: ${pluginManager.hasPlugin('com.palantir.baseline-checkstyle')}"
                }
            }
            """);

        InvocationResult result = gradle.withArgs("verifyPlugin").buildsSuccessfully();
        assertThat(result).output().contains("HAS_BASELINE_CHECKSTYLE: true");
    }

    @Test
    void checkstyle_plugin_applied(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            tasks.register('verifyPlugin') {
                doLast {
                    println "HAS_CHECKSTYLE: ${pluginManager.hasPlugin('checkstyle')}"
                }
            }
            """);

        InvocationResult result = gradle.withArgs("verifyPlugin").buildsSuccessfully();
        assertThat(result).output().contains("HAS_CHECKSTYLE: true");
    }

    @Test
    void does_not_include_resources(GradleInvoker gradle, RootProject rootProject) {
        rootProject.file("src/test/resources/checkstyle.xml").overwrite("""
            <?xml version="1.0"?>
            """);

        rootProject.buildGradle().append("""
            tasks.register('verifyCheckstyleSources') {
                doLast {
                    def resourceFile = file('src/test/resources/checkstyle.xml')
                    tasks.withType(Checkstyle).each { task ->
                        assert !task.source.files.contains(resourceFile) : "Checkstyle task ${task.name} should not include ${resourceFile}"
                    }
                }
            }
            """);

        gradle.withArgs("verifyCheckstyleSources").buildsSuccessfully();
    }
}
