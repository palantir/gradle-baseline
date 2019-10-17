package com.palantir.baseline.tasks.dependencies

import com.palantir.baseline.AbstractPluginTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DependencyReportTaskTests extends AbstractDependencyTest {

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
            allDependencies:
            - com.google.guava:guava
            apiDependencies: []
            implicitDependencies:
            - com.google.guava:guava
            unusedDependencies:
            - com.fasterxml.jackson.datatype:jackson-datatype-guava
        '''
        String actual = reportFile.text
        expected == actual
    }

    def 'test writing dep report with api deps'() {
        setup:
        setupMultiProject()

        when:
        BuildResult result = runTask('analyzeMainDeps')

        then:
        result.task(':analyzeMainDeps').getOutcome() == TaskOutcome.SUCCESS
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

}
