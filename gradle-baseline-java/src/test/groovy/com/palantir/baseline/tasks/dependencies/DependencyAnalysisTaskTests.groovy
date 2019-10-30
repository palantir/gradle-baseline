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

package com.palantir.baseline.tasks.dependencies

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DependencyAnalysisTaskTests extends AbstractDependencyTest {

    File reportDir
    File reportFile

    def setup() {
        reportDir = new File(projectDir, "build/reports")
        reportFile = new File(reportDir, "analyzeDeps-report.yaml")
    }

    def 'empty project should work'() {
        setup:
        buildFile << standardBuildFile

        when:
        BuildResult result = runTask('analyzeDeps')

        then:
        result.task(':analyzeDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected = cleanFileContentsWithEol '''
            allDependencies: []
            apiDependencies: []
            implicitDependencies: []
            unusedDependencies: []
        '''
        String actual = reportFile.text
        expected == actual
    }

    def 'dependency report has proper contents'() {
        setup:
        setupTransitiveJarDependencyProject()
        //include some annotation stuff to make sure not listed as unused
        //and a compileOnly to make sure we pick up things from it
        buildFile << """
        dependencies {
            annotationProcessor 'org.immutables:value:2.7.5'
            compileOnly 'org.immutables:value:2.7.5:annotations'
        }
        """

        when:
        BuildResult result = runTask('analyzeDeps')

        then:
        result.task(':analyzeDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected = cleanFileContentsWithEol '''
            allDependencies:
            - com.google.guava:guava
            apiDependencies: []
            implicitDependencies:
            - com.google.guava:guava
            unusedDependencies:
            - com.fasterxml.jackson.datatype:jackson-datatype-guava
            - org.immutables:value::annotations
        '''
        String actual = reportFile.text
        expected == actual
    }

    def 'multi-module report'() {
        setup:
        setupMultiProject()

        when:
        BuildResult result = runTask('analyzeDeps')

        then:
        result.task(':analyzeDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected = cleanFileContentsWithEol '''
            allDependencies:
            - com.google.guava:guava
            - project :sub-project-no-deps
            apiDependencies:
            - com.google.guava:guava
            implicitDependencies:
            - com.google.guava:guava
            - project :sub-project-no-deps
            unusedDependencies:
            - project :sub-project-jar-deps
            - project :sub-project-with-deps
        '''
        String actual = reportFile.text
        expected == actual
    }

    def 'analyze test source set'() {
        setup:
        setupMultiProject()
        reportFile = new File(reportDir, "analyzeTestDeps-report.yaml")

        when:
        BuildResult result = runTask('analyzeTestDeps')

        then:
        result.task(':analyzeTestDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected = cleanFileContentsWithEol '''
            allDependencies:
            - com.google.guava:guava
            - junit:junit
            apiDependencies:
            - junit:junit
            implicitDependencies:
            - com.google.guava:guava
            unusedDependencies:
            - com.fasterxml.jackson.datatype:jackson-datatype-guava
        '''
        String actual = reportFile.text
        expected == actual
    }
}
