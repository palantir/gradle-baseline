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
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.apache.commons.io.FileUtils

class BaselineEclipseIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'com.palantir.baseline-eclipse'
    '''.stripIndent()

    def noJavaBuildFile = '''
        apply plugin: 'com.palantir.baseline-eclipse'
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
        ExecutionResult result = runTasksSuccessfully('eclipse')
        assert result.wasExecuted(':eclipseTemplate')
    }

    def 'Eclipse task works without Java plugin'() {
        when:
        buildFile << noJavaBuildFile

        then:
        ExecutionResult result = runTasksSuccessfully('eclipse')
        assert result.wasExecuted(':eclipseTemplate')
    }

    def 'Eclipse task sets jdt core properties'() {
        when:
        buildFile << standardBuildFile

        then:
        runTasksSuccessfully('eclipse')
        def jdtCorePrefs = Files.asCharSource(new File(projectDir,
            ".settings/org.eclipse.jdt.core.prefs"), Charsets.UTF_8).read()
        jdtCorePrefs.contains("org.eclipse.jdt.core.compiler.annotation.nullable=javax.annotation.CheckForNull")
    }
}
