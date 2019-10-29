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

    def 'dot files contain proper contents'() {
        setup:
        File fullReportFile = new File(mainReportDir, "main.dot")
        File apiReportFile = new File(mainReportDir, "api/main.dot")
        setupMultiProject()

        when:
        BuildResult result = runTask('findDeps')

        then:
        result.task(':findDeps').getOutcome() == TaskOutcome.SUCCESS
        checkReportContents fullReportFile, '''
digraph "main" {
    
   "com.p0.RootTestClassWithDeps"                     -> "com.p1.TestClassNoDeps2 (not found)";
   "com.p0.RootTestClassWithJarDep"                   -> "com.google.common.collect.ImmutableList (not found)";
}'''

        checkReportContents apiReportFile, '''
digraph "main" {
    
   "com.p0.RootTestClassWithJarDep"                   -> "com.google.common.collect.ImmutableList (not found)";
}'''

        when:
        result = runTask('findDeps')

        then:
        result.task(':findDeps').getOutcome() == TaskOutcome.UP_TO_DATE

    }

    private void checkReportContents(File reportFile, String rawExpected) {
        assert reportFile.exists()
        // strip out the comment line with the path to the class files because don't care about it
        // and it changes with every test run
        String actual = reportFile.text.replaceAll('// Path.*', '')
        String expected = cleanFileContentsWithEol(rawExpected)
        assert expected == actual
    }

}
