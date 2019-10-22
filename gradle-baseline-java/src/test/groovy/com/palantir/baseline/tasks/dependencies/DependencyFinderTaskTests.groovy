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

class DependencyFinderTaskTests extends AbstractDependencyTest {

    File rootReportDir
    File mainReportDir

    def setup() {
        rootReportDir = new File(projectDir, "build/reports/dep-dot-files")
        mainReportDir = new File(rootReportDir, "findDeps")
    }

    def 'dot file contains proper content'() {
        setup:
        File fullReportFile = new File(mainReportDir, "main.dot")
        File summaryReportFile = new File(mainReportDir, "summary.dot")
        File apiReportFile = new File(mainReportDir, "api/summary.dot")
        setupTransitiveJarDependencyProject()

        when:
        BuildResult result = runTask('findDeps')

        then:
        result.task(':findDeps').getOutcome() == TaskOutcome.SUCCESS
        checkReportContents fullReportFile, '''
digraph "main" {
    
   "pkg.Foo"                                          -> "com.google.common.collect.ImmutableList (guava-18.0.jar)";
}'''

        checkReportContents summaryReportFile, '''
digraph "summary" {
  "pkg"                                              -> "com.google.common.collect (guava-18.0.jar)";
}'''

        checkReportContents apiReportFile, '''
digraph "summary" {
}'''

    }

    private void checkReportContents(File fullReportFile, String rawExpected) {
        assert fullReportFile.exists()
        // strip out the comment line with the path to the class files because don't care about it
        // and it changes with every test run
        String actual = fullReportFile.text.replaceAll('// Path.*', '')
        String expected = cleanFileContentsWithEol(rawExpected)
        assert expected == actual
    }

}
