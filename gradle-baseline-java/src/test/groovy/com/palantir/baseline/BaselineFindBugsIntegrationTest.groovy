package com.palantir.baseline

import nebula.test.IntegrationSpec
import org.apache.commons.io.FileUtils

class BaselineFindBugsIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'com.palantir.baseline-findbugs'
        repositories { jcenter() }
        sourceSets {
            generated
            partiallyGenerated
        }
    '''.stripIndent()

    def badClass = """
            public class Bad {
                byte[] bar = "bar".getBytes();  // FindBugs error
            }
        """.stripIndent()

    def goodClass = """
            public class Good {}
        """.stripIndent()

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

    def 'src/main files are not excluded by default'() {
        when:
        buildFile << standardBuildFile
        writeJavaFile("main", "foo", "Bad", badClass)

        then:
        runTasksWithFailure('findbugsMain')
    }

    def 'src/generated files are excluded by default'() {
        when:
        buildFile << standardBuildFile
        writeJavaFile("generated", "foo", "Bad", badClass)

        then:
        runTasksSuccessfully('compileGeneratedJava', 'findbugsGenerated')
    }

    def 'can configure the set of excluded files'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            baselineFindbugs {
                exclude ~/\\/partiallyGenerated\\//
            }
        '''.stripIndent()
        writeJavaFile("main", "foo", "Bad", badClass)
        writeJavaFile("generated", "foo", "Bad", badClass)
        writeJavaFile("partiallyGenerated", "foo", "Bad", badClass)
        writeJavaFile("partiallyGenerated", "foo", "Good", goodClass)

        then:
        runTasksSuccessfully('findbugsGenerated', 'findbugsPartiallyGenerated')
        runTasksWithFailure('findbugsMain')
    }

    def writeJavaFile(String sourceSet, String packageDotted, String className, String classLines){
        def path = 'src/' + sourceSet + '/java/' + packageDotted.replace('.', '/') + '/' + className + '.java'
        def javaFile = createFile(path, projectDir)
        javaFile << """package ${packageDotted};
            ${classLines}
        """.stripIndent()
    }
}
