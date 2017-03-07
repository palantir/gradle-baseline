/*
 * Copyright 2015 Palantir Technologies, Inc.
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

import com.google.common.base.Charsets
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome

class BaselineEclipseIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-eclipse'
        }
    '''.stripIndent()

    def noJavaBuildFile = '''
        plugins {
            id 'com.palantir.baseline-eclipse'
        }
    '''.stripIndent()

    def setup() {
        FileUtils.copyDirectory(
            new File("../gradle-baseline-java-config/resources"),
            new File(projectDir, ".baseline"))
    }

    def 'Eclipse task depends on eclipseTemplate'() {
        when:
        buildFile << standardBuildFile

        then:
        def result = with('eclipse').build()
        result.task(':eclipseTemplate').outcome == TaskOutcome.SUCCESS
    }

    def 'Eclipse task works without Java plugin'() {
        when:
        buildFile << noJavaBuildFile

        then:
        def result = with('eclipse').build()
        result.task(':eclipseTemplate').outcome == TaskOutcome.SKIPPED
    }

    def 'Eclipse task sets jdt core properties'() {
        when:
        buildFile << standardBuildFile

        then:
        with('eclipse').build()
        def jdtCorePrefs = Files.asCharSource(new File(projectDir,
            ".settings/org.eclipse.jdt.core.prefs"), Charsets.UTF_8).read()
        jdtCorePrefs.contains("org.eclipse.jdt.core.compiler.annotation.nullable=javax.annotation.CheckForNull")
    }

    def 'Eclipse task sets correct Java version from sourceCompatibility property'() {
        when:
        buildFile << standardBuildFile
        buildFile << "sourceCompatibility = '1.3'"  // use '1.3' since it cannot be the default

        then:
        with('eclipse').build()
        def jdtCorePrefs = Files.asCharSource(new File(projectDir,
            ".settings/org.eclipse.jdt.core.prefs"), Charsets.UTF_8).read()
        jdtCorePrefs.contains("org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.3")
        jdtCorePrefs.contains("org.eclipse.jdt.core.compiler.source=1.3")
        jdtCorePrefs.contains("org.eclipse.jdt.core.compiler.compliance=1.3")
    }
}
