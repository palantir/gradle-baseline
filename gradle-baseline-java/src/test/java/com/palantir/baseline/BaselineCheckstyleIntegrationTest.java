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
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineCheckstyleIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) throws Exception {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                rootProject.directory(".baseline").path().toFile());
    }

    GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-checkstyle");

        return rootProject.buildGradle().append("""
            repositories {
                // to resolve the `checkstyle` configuration
                mavenCentral()
            }
            """);
    }

    private static final String EXAMPLE_JAVA_FILE = """
        package example;

        public class Example {
        }
        """;

    @Test
    void checkstyle_main_succeeds(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass(EXAMPLE_JAVA_FILE);

        InvocationResult result = gradle.withArgs("checkstyleMain").buildsSuccessfully();
        assertThat(result).task(":checkstyleMain").succeeded();
    }
}
