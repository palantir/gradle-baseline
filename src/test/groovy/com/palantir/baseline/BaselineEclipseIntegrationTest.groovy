package com.palantir.baseline

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class BaselineEclipseIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'baseline-eclipse'
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
            apply plugin: 'baseline-eclipse'
        '''.stripIndent()

        then:
        ExecutionResult result = runTasksWithFailure('eclipse')
        assert result.standardError.contains("Caused by: java.lang.NullPointerException: " +
                "The baseline-eclipse plugin requires the java plugin to be applied.")
    }
}
