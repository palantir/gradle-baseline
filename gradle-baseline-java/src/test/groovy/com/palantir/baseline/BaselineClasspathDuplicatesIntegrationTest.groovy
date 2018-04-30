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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineClasspathDuplicatesIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = """
        plugins {
            id 'java'
            id 'com.palantir.baseline-classpath-duplicates'
        }
    """.stripIndent()

    def 'Plugin can be applied'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result = with('tasks', '--stacktrace').build()
        assert result.getOutput().contains("checkUniqueClassNames aaa")
    }

    def 'Task should run as part of :check'() {
        when:
        buildFile << standardBuildFile

        then:
        def result = with('check', '--stacktrace').build()
        result.task(':checkUniqueClassNames').outcome == TaskOutcome.SUCCESS
    }
}
