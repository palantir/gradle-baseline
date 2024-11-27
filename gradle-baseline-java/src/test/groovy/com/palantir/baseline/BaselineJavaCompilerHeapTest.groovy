/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.plugins.BaselineJavaCompilerHeap
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineJavaCompilerHeapTest extends Specification {

    def testDefault() {
        when:
        def project = ProjectBuilder.builder().build()
        project.buildscript {
            repositories {
                mavenCentral()
            }
        }
        project.plugins.apply 'java'
        project.plugins.apply BaselineJavaCompilerHeap
        project.evaluate()

        then:
        JavaCompile compileTask = project.tasks.getByName('compileJava').asType(JavaCompile.class)
        compileTask.options.forkOptions.memoryMaximumSize == "2g"
    }

    def testOverridden() {
        when:
        def project = ProjectBuilder.builder().build()
        project.buildscript {
            repositories {
                mavenCentral()
            }
        }
        project.plugins.apply 'java'
        project.tasks.compileJava {
            options.forkOptions.memoryMaximumSize = "768m"
        }
        project.plugins.apply BaselineJavaCompilerHeap
        project.evaluate()

        then:
        JavaCompile compileTask = project.tasks.getByName('compileJava').asType(JavaCompile.class)
        compileTask.options.forkOptions.memoryMaximumSize == "768m"
    }
}
