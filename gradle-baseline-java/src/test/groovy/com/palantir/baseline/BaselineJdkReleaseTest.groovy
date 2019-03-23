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

package com.palantir.baseline

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

import com.palantir.baseline.plugins.BaselineJdkRelease
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineJdkReleaseTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineJdkRelease
        project.getConvention().getPlugin(JavaPluginConvention.class).sourceCompatibility = 7
        project.getConvention().getPlugin(JavaPluginConvention.class).targetCompatibility = 8
        project.evaluate()
    }

    def jdkReleasePluginApplied() {
        expect:
        assertTrue project.plugins.hasPlugin(BaselineJdkRelease.class)
    }

    def baselineFormatCreatesFormatTask() {
        JavaCompile javaCompile = project.getTasks().named("compileJava", JavaCompile.class).get()
        expect:
        if (JavaVersion.current().compareTo(JavaVersion.VERSION_1_8) > 0) {
            // only supported in JDK 9+
            assertTrue javaCompile.options.compilerArgs.contains('--release')
            assertTrue javaCompile.options.compilerArgs.contains('8')
        } else {
            assertFalse javaCompile.options.compilerArgs.contains('--release')
            assertFalse javaCompile.options.compilerArgs.contains('8')
        }
    }
}
