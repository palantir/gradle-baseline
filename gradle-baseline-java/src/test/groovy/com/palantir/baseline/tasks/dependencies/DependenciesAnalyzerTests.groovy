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

class DependenciesAnalyzerTests extends AbstractDependencyTest {

    def 'findNecessaryArtifacts returns proper project dependency'() {
        setup:
        setupMultiProject()
        buildFile << """
        task('printDeps', dependsOn:findMainDeps) {
            doLast {
            println "Here"
            def f = project.tasks.named('findMainDeps').get().reportFile.get().getAsFile()
            def analyzer = new com.palantir.baseline.tasks.dependencies.DependencyAnalyzer(project, [configurations.compileClasspath], files(f), files(f), [] as Set)
            def artifacts = analyzer.getAllRequiredArtifacts()
            println 'Num deps: ' + artifacts.size();
            artifacts.each {println 'DEP: ' + com.palantir.baseline.tasks.dependencies.DependencyUtils.getArtifactName(it)}
            }
        }
        """.stripIndent()

        when:
        BuildResult result = with('printDeps', '--stacktrace').withDebug(true).build()

        then:
        !result.output.contains("GradleScriptException")
        result.task(':printDeps').getOutcome() == TaskOutcome.SUCCESS
        result.output.contains("Num deps: 2")
        result.output.contains("DEP: project :sub-project-no-deps")

    }

}
