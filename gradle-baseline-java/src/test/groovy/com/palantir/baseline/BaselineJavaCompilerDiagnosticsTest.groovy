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

package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineJavaCompilerDiagnostics
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineJavaCompilerDiagnosticsTest extends Specification {

    def testDefault() {
        when:
        def project = ProjectBuilder.builder().build()
        project.buildscript {
            repositories {
                mavenCentral()
            }
        }
        project.plugins.apply 'java'
        project.plugins.apply BaselineJavaCompilerDiagnostics
        project.evaluate()

        then:
        JavaCompile compileTask = project.tasks.getByName('compileJava').asType(JavaCompile.class)
        List<String> allJvmArgs = compileTask.allJvmArgs
        allJvmArgs.stream().filter('-Xmaxerrs'::equals).count() == 1
        allJvmArgs.get(allJvmArgs.indexOf('-Xmaxerrs') + 1) == '1000'
        allJvmArgs.stream().filter('-Xmaxwarns'::equals).count() == 1
        allJvmArgs.get(allJvmArgs.indexOf('-Xmaxerrs') + 1) == '1000'
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
        tasks.withType(JavaCompile) {
            options.compilerArgs += [
                    '-Xmaxerrs', '1000'
            ]
        }
        project.plugins.apply BaselineJavaCompilerDiagnostics
        project.evaluate()

        then:
        project.tasks.type
        JavaCompile compileTask = project.tasks.getByName('compileJava').asType(JavaCompile.class)
        List<String> allJvmArgs = compileTask.allJvmArgs
        allJvmArgs.stream().filter('-Xmaxerrs'::equals).count() == 1
        allJvmArgs.get(allJvmArgs.indexOf("-Xmaxerrs") + 1) == '1000'
        allJvmArgs.stream().filter('-Xmaxwarns'::equals).count() == 1
    }
}
