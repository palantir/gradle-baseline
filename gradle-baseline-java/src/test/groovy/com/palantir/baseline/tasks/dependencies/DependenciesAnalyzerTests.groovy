package com.palantir.baseline.tasks.dependencies

import com.palantir.baseline.AbstractPluginTest
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
