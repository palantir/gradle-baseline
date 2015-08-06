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

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class BaselineEclipseIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'com.palantir.baseline-eclipse'
    '''.stripIndent()

    def 'Eclipse task depends on eclipseTemplate'() {
        when:
        buildFile << standardBuildFile

        then:
        ExecutionResult result = runTasksSuccessfully('eclipse')
        result.wasExecuted(':eclipseTemplate')
    }

    def 'Eclipse requires the java plugin to be applied'() {
        when:
        buildFile << '''
            apply plugin: 'com.palantir.baseline-eclipse'
        '''.stripIndent()

        then:
        ExecutionResult result = runTasksWithFailure('eclipse')
        assert result.standardError.contains("Caused by: java.lang.NullPointerException: " +
                "The baseline-eclipse plugin requires the java plugin to be applied.")
    }
}
