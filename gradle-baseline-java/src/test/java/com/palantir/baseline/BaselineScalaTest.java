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
class BaselineScalaTest {

    private static final String VALID_SCALA_FILE = """
        package test;
        case class Test(field: Int)
        """;

    private static final String VALID_JAVA_FILE = """
        package test;
        public class Test { }
        """;

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("scala").add("idea").add("com.palantir.baseline-scala");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'org.scala-lang:scala-library:2.13.12'
            }
            """);
    }

    @Test
    void baseline_scala_plugin_applied(GradleInvoker gradle, RootProject rootProject) {
        // Verify the plugin applies successfully by running a basic Scala compilation
        rootProject.sourceSet("main").srcDir("scala").file("test/Test.scala").overwrite(VALID_SCALA_FILE);

        InvocationResult result = gradle.withArgs("compileScala").buildsSuccessfully();

        assertThat(result).task(":compileScala").succeeded();
    }

    @Test
    void configures_target_jvm_version(GradleInvoker gradle, RootProject rootProject) {
        rootProject.sourceSet("main").srcDir("scala").file("test/Test.scala").overwrite(VALID_SCALA_FILE);

        rootProject.buildGradle().append("""
            tasks.withType(ScalaCompile).configureEach { task ->
                doFirst {
                    logger.lifecycle("ScalaCompileOptions: ${task.scalaCompileOptions.additionalParameters}")
                }
            }
            """);

        InvocationResult result = gradle.withArgs("compileScala").buildsSuccessfully();

        assertThat(result).output().contains("-target:jvm-1.8");
    }

    @Test
    void configures_scala_mixed_mode(GradleInvoker gradle, RootProject rootProject) {
        rootProject.directory("src/main/scala/test").file("Test.java").overwrite(VALID_JAVA_FILE);

        rootProject.buildGradle().append("""
            idea {
                project {
                    ipr {
                        withXml { xmlProvider ->
                            logger.lifecycle("IDEA XML: ${xmlProvider.asString()}")
                        }
                    }
                }
            }
            """);

        InvocationResult result = gradle.withArgs("ideaProject").buildsSuccessfully();

        assertThat(result).output().contains("value=\"Mixed\"");
    }
}
