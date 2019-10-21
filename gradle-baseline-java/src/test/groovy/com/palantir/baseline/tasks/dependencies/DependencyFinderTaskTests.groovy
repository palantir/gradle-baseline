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

    def setup() {
        rootReportDir = new File(projectDir, "build/reports/dep-dot-files")
    }

    def 'output report file path property correct'() {
        setup:
        File reportFile = new File(rootReportDir, "findMainDeps/main.dot")
        setupTransitiveJarDependencyProject()
        buildFile << """
        project.tasks.named('findMainDeps').configure {
            doLast {t ->
                println "REPORTFILE: " + t.reportFile.getAsFile().get().getAbsolutePath()
            }
        }
        """.stripIndent()

        when:
        BuildResult result = runTask('findMainDeps')

        then:
        result.task(':findMainDeps').getOutcome() == TaskOutcome.SUCCESS
        result.output.contains(reportFile.absolutePath)
    }

    def 'dot file contains proper content'() {
        setup:
        File reportFile = new File(rootReportDir, "findMainDeps/main.dot")
        setupTransitiveJarDependencyProject()

        when:
        BuildResult result = runTask('findMainDeps')

        then:
        result.task(':findMainDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected =  cleanFileContentsWithEol'''
digraph "main" {
    
   "pkg.Foo"                                          -> "com.google.common.collect.ImmutableList (not found)";
}'''
        reportFile.exists()
        // strip out the comment line with the path to the class files because don't care about it
        // and it changes with every test run
        String actual = reportFile.text.replaceAll('// Path.*', '')
        expected == actual
    }

    def 'apionly report'() {
        setup:
        File reportFile = new File(rootReportDir, "findMainApiDeps/main.dot")
        setupMultiProject()

        when:
        BuildResult result = runTask('findMainApiDeps')

        then:
        result.task(':findMainApiDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected =  cleanFileContentsWithEol'''
digraph "main" {
    
   "com.p0.RootTestClassWithJarDep"                   -> "com.google.common.collect.ImmutableList (not found)";
}'''
        reportFile.exists()
        // strip out the comment line with the path to the class files because don't care about it
        // and it changes with every test run
        String actual = reportFile.text.replaceAll('// Path.*', '')
        expected == actual
    }

}
