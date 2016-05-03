package com.palantir.baseline

import nebula.test.IntegrationSpec
import org.apache.commons.io.FileUtils

class BaselineFindBugsIntegrationTest extends IntegrationSpec {
    def standardBuildFile = '''
        apply plugin: 'java'
        apply plugin: 'com.palantir.baseline-findbugs'
    '''.stripIndent()

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

    def 'src/generated files are excluded'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
            sourceSets {
                generated
                partiallyGenerated
            }
            repositories { jcenter() }
        """.stripIndent()
        def badClass = """
            public class Foo {
                byte[] bar = "bar".getBytes();  // FindBugs error
            }
        """.stripIndent()
        def goodClass = """
            public class Bar {}
        """.stripIndent()
        writeJavaFile("main", "foo", "Foo", badClass)  // Fails findbugs
        writeJavaFile("generated", "foo", "Foo", badClass)  // Succeeds because we exclude /generated/
        writeJavaFile("partiallyGenerated", "foo", "Foo", badClass)  // Succeeds because we exclude /partiallyGenerated/
        writeJavaFile("partiallyGenerated", "foo", "Bar", goodClass)

        then:
        runTasksSuccessfully('compileJava', 'compileGeneratedJava', 'compilePartiallyGeneratedJava')
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
