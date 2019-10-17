package com.palantir.baseline.tasks.dependencies

import com.palantir.baseline.AbstractPluginTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DependencyOptimizationReportTaskTests extends AbstractDependencyTest {

    File reportDir
    File reportFile

    def setup() {
        reportDir = new File(projectDir, "build/reports")
        reportFile = new File(reportDir, "analyzeMainDeps-report.yaml")
    }

    def 'test writing dep report'() {
        setup:
        setupTransitiveJarDependencyProject()

        when:
        BuildResult result = runTask('analyzeMainDeps')

        then:
        result.task(':analyzeMainDeps').getOutcome() == TaskOutcome.SUCCESS
        String expected = cleanFileContentsWithEol '''
            implicitDependencies:
            - com.google.guava:guava
            necessaryDependencies:
            - com.google.guava:guava
            unusedDependencies:
            - com.fasterxml.jackson.datatype:jackson-datatype-guava
        '''
        String actual = reportFile.text
        expected == actual
    }

}
