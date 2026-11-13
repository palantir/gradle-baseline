/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline

import com.palantir.gradle.plugintesting.GradleTestVersions
import spock.lang.Unroll

import java.nio.file.Files
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineClassUniquenessPluginIntegrationTest extends AbstractPluginTest {

    private static final String FOO_CLASS = 'com.example.Foo'
    private static final String FOO_CLASS_FILE = 'com/example/Foo.class'
    private static final Pattern FOO_AND_BAR_CONFLICT_ON_BOTH_CLASSPATHS = ~/(?s)## compileClasspath\s+\[\S+:bar, \S+:foo\]\s+- com\.something\.MyClass\s+.*## runtimeClasspath\s+\[\S+:bar, \S+:foo\]\s+- com\.something\.MyClass\s/

    def standardBuildFile = """
        plugins {
            id 'java-library'
            id 'com.palantir.baseline-class-uniqueness'
        }
        subprojects {
            apply plugin: 'java-library'
        }
        repositories {
            mavenCentral()
        }
    """.stripIndent()

    @Unroll
    def '#gradleVersion: detect duplicates in two external jars, then --fix captures'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
            api group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }
        """.stripIndent()
        BuildResult result = with('check', '-s').withGradleVersion(gradleVersion).buildAndFail()

        then:
        result.getOutput().contains("baseline-class-uniqueness detected multiple jars containing identically named classes.")
        result.getOutput().contains("[javax.el:javax.el-api, javax.servlet.jsp:jsp-api]")
        result.getOutput().contains("javax.el.ArrayELResolver");
        !lockfile.exists()

        when:
        with("checkClassUniqueness", "--fix").withGradleVersion(gradleVersion).build()

        then:
        lockfile.exists()
        File expected = new File("src/test/resources/com/palantir/baseline/baseline-class-uniqueness.expected.lock")
        if (Boolean.getBoolean("recreate")) {
            Files.writeString(expected.toPath(), lockfile.text)
        }
        lockfile.text == expected.text

        where:
        gradleVersion << GradleTestVersions.gradleVersionsForTests
    }

    def 'detect duplicates in two external jars, then --write-locks captures'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
            api group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }
        """.stripIndent()
        BuildResult result = with('check', '-s').buildAndFail()

        then:
        result.getOutput().contains("baseline-class-uniqueness detected multiple jars containing identically named classes.")
        result.getOutput().contains("[javax.el:javax.el-api, javax.servlet.jsp:jsp-api]")
        result.getOutput().contains("javax.el.ArrayELResolver");
        !lockfile.exists()

        when:
        with("checkClassUniqueness", "--write-locks").build()

        then:
        lockfile.exists()
        File expected = new File("src/test/resources/com/palantir/baseline/baseline-class-uniqueness.expected.lock")
        if (Boolean.getBoolean("recreate")) {
            Files.writeString(expected.toPath(), lockfile.text)
        }
        lockfile.text == expected.text
    }

    def 'detect duplicates in two external jars with the same ModuleVersionIdentifier but different classifiers'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api group: 'com.google.cloud.bigdataoss', name: 'gcs-connector', version: 'hadoop3-2.2.19'
            api group: 'com.google.cloud.bigdataoss', name: 'gcs-connector', version: 'hadoop3-2.2.19', classifier: 'shaded'
        }
        """.stripIndent()
        BuildResult result = with('check', '-s').buildAndFail()

        then:
        result.getOutput().contains("baseline-class-uniqueness detected multiple jars containing identically named classes.")
        result.getOutput().contains("[com.google.cloud.bigdataoss:gcs-connector, com.google.cloud.bigdataoss:gcs-connector (classifier=shaded)]")
        result.getOutput().contains("com.google.cloud.hadoop.fs.gcs.FsBenchmark")

        when:
        with("checkClassUniqueness", "--write-locks").build()

        then:
        lockfile.exists()
        File expected = new File("src/test/resources/com/palantir/baseline/baseline-class-uniqueness-with-classifier.expected.lock")
        if (Boolean.getBoolean("recreate")) {
            Files.writeString(expected.toPath(), lockfile.text)
        }
        lockfile.text == expected.text
    }

    def 'detect duplicates in two external jars in non-standard configuration'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        configurations {
            myConf
        }
        dependencies {
            myConf group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
            myConf group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }

        checkClassUniqueness {
              configurations.add project.configurations.myConf
        }
        """.stripIndent()
        BuildResult result = with('check', '-s').buildAndFail()

        then:
        result.output.contains("baseline-class-uniqueness detected multiple jars containing identically named classes.")
        result.getOutput().contains("## myConf")
        println result.getOutput()
    }

    def 'ignores duplicates when the implementations are identical'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api 'com.palantir.tritium:tritium-api:0.9.0'
            api 'com.palantir.tritium:tritium-core:0.9.0'
        }
        """.stripIndent()

        then:
        with('checkClassUniqueness', '-s').build()
    }

    def 'ignores duplicates based on module-info and UnusedStubClass'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            // depends on spark-network-common, which also contains UnusedStubClass. Also depends on versions of Jackson
            // that use module-info.java.
            api 'org.apache.spark:spark-network-shuffle_2.13:3.3.0'
        }
        """.stripIndent()

        then:
        with('checkClassUniqueness', '-s').build()
    }

    def 'task should be up-to-date when classpath is unchanged'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result1 = with('checkClassUniqueness').build()
        result1.task(':checkClassUniqueness').outcome == TaskOutcome.SUCCESS

        BuildResult result = with('checkClassUniqueness').build()
        result.task(':checkClassUniqueness').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'passes when no duplicates are present'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api 'com.google.guava:guava:19.0'
            api 'org.apache.commons:commons-io:1.3.2'
            api 'junit:junit:4.12'
            api 'com.netflix.nebula:nebula-test:6.4.2'
        }
        """.stripIndent()
        BuildResult result = with('checkClassUniqueness', '--info').build()

        then:
        result.task(":checkClassUniqueness").outcome == TaskOutcome.SUCCESS
        println result.getOutput()
        !new File(projectDir, "baseline-class-uniqueness.lock").exists()
    }

    def '--fix removes lock file when it is not needed'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api 'com.google.guava:guava:19.0'
            api 'org.apache.commons:commons-io:1.3.2'
            api 'junit:junit:4.12'
            api 'com.netflix.nebula:nebula-test:6.4.2'
        }
        """.stripIndent()
        new File(projectDir, "baseline-class-uniqueness.lock").text = """
        # Dummy lock file that should be removed
        """
        BuildResult result = with('checkClassUniqueness', '--fix').build()

        then:
        result.task(":checkClassUniqueness").outcome == TaskOutcome.SUCCESS
        println result.getOutput()
        !new File(projectDir, "baseline-class-uniqueness.lock").exists()
    }

    def 'should detect duplicates from transitive dependencies'() {
        when:
        multiProject.addSubproject('foo', """
        dependencies {
            api group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
        }
        """)
        multiProject.addSubproject('bar', """
        dependencies {
            api group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }
        """)

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':foo')
            api project(':bar')
        }
        """.stripIndent()

        then:
        BuildResult result = with('checkClassUniqueness', '-s').buildAndFail()
        result.output.contains("baseline-class-uniqueness detected multiple jars containing identically named classes")
    }

    def 'detects duplicates in project jars'() {
        when:
        multiProject.addSubproject('foo')
        multiProject.addSubproject('bar')
        file('foo/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int foo; }'
        file('bar/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int bar; }'

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':foo')
            api project(':bar')
        }
        """.stripIndent()

        then:
        BuildResult result = with('checkClassUniqueness').buildAndFail()
        result.output =~ /\[\S+:bar, \S+:foo\]\s+- com\.something\.MyClass/
    }

    def 'ignores file dependencies'() {
        when:
        writeJar('libs/one.jar', [(FOO_CLASS_FILE): 'one'])
        writeJar('libs/two.jar', [(FOO_CLASS_FILE): 'two'])
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api files('libs/one.jar', 'libs/two.jar')
        }
        """.stripIndent()

        then: "file dependencies have no coordinates to show in the lock file"
        with('checkClassUniqueness').build()
    }

    def 'labels snapshot jars with a classifier'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        String timestamped = '1.0-20250101.123456-1'
        writeJar("repo/g/bar/1.0-SNAPSHOT/bar-${timestamped}.jar", [(FOO_CLASS_FILE): 'main'])
        writeJar("repo/g/bar/1.0-SNAPSHOT/bar-${timestamped}-tests.jar", [(FOO_CLASS_FILE): 'tests'])
        writePom('g', 'bar', '1.0-SNAPSHOT', "repo/g/bar/1.0-SNAPSHOT/bar-${timestamped}.pom")
        file('repo/g/bar/1.0-SNAPSHOT/maven-metadata.xml').text = """\
            <metadata><groupId>g</groupId><artifactId>bar</artifactId><version>1.0-SNAPSHOT</version>
            <versioning><snapshot><timestamp>20250101.123456</timestamp><buildNumber>1</buildNumber></snapshot>
            <lastUpdated>20250101123456</lastUpdated></versioning></metadata>
            """.stripIndent()

        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            api 'g:bar:1.0-SNAPSHOT'
            api 'g:bar:1.0-SNAPSHOT:tests'
        }
        """.stripIndent()
        with('checkClassUniqueness', '--fix').build()

        then:
        lockfile.text == lockFile('runtimeClasspath', '[g:bar, g:bar (classifier=tests)]', FOO_CLASS)
    }

    def 'labels jars whose module name ends in a dash and digits'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        writeJar('repo/g/lib-2/2/lib-2-2.jar', [(FOO_CLASS_FILE): 'main'])
        writeJar('repo/g/lib-2/2/lib-2-2-jdk-2.jar', [(FOO_CLASS_FILE): 'jdk'])
        writePom('g', 'lib-2', '2', 'repo/g/lib-2/2/lib-2-2.pom')

        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            api 'g:lib-2:2'
            api 'g:lib-2:2:jdk-2'
        }
        """.stripIndent()
        with('checkClassUniqueness', '--fix').build()

        then:
        lockfile.text == lockFile('runtimeClasspath', '[g:lib-2, g:lib-2 (classifier=jdk-2)]', FOO_CLASS)
    }

    def 'checks jars that a POM depends on with type test-jar'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        writeJar('repo/g/lib/1.0/lib-1.0-tests.jar', [(FOO_CLASS_FILE): 'tests'])
        writePom('g', 'lib', '1.0', 'repo/g/lib/1.0/lib-1.0.pom')
        writeJar('repo/g/consumer/1.0/consumer-1.0.jar', [:])
        writePom('g', 'consumer', '1.0', 'repo/g/consumer/1.0/consumer-1.0.pom', '''\
            <dependencies><dependency><groupId>g</groupId><artifactId>lib</artifactId><version>1.0</version>
            <type>test-jar</type></dependency></dependencies>
            '''.stripIndent())
        writeOtherModule()

        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            api 'g:consumer:1.0'
            api 'g:other:1.0'
        }
        """.stripIndent()
        with('checkClassUniqueness', '--fix').build()

        then: "Gradle gives the tests jar the artifact type test-jar, from the consumer's POM"
        lockfile.text == lockFile('runtimeClasspath', '[g:lib (classifier=tests), g:other]', FOO_CLASS)
    }

    def 'checks jars of modules with a packaging Gradle does not know'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        writeJar('repo/g/component/1.0/component-1.0.jar', [(FOO_CLASS_FILE): 'component'])
        writePom('g', 'component', '1.0', 'repo/g/component/1.0/component-1.0.pom',
                '<packaging>takari-maven-component</packaging>')
        writeOtherModule()

        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            api 'g:component:1.0'
            api 'g:other:1.0'
        }
        """.stripIndent()
        with('checkClassUniqueness', '--fix').build()

        then: "Gradle gives the jar the artifact type takari-maven-component, from its packaging"
        lockfile.text == lockFile('runtimeClasspath', '[g:component, g:other]', FOO_CLASS)
    }

    def 'ignores artifacts that are not jars'() {
        when:
        writeJar('repo/g/archive/1.0/archive-1.0.zip', [(FOO_CLASS_FILE): 'zip'])
        writePom('g', 'archive', '1.0', 'repo/g/archive/1.0/archive-1.0.pom')
        writeJar('repo/g/android/1.0/android-1.0.aar', [(FOO_CLASS_FILE): 'aar'])
        writePom('g', 'android', '1.0', 'repo/g/android/1.0/android-1.0.pom', '<packaging>aar</packaging>')
        writeOtherModule()

        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            api 'g:archive:1.0@zip'
            api 'g:android:1.0'
            api 'g:other:1.0'
        }
        """.stripIndent()

        then: "only jars are indexed, though the zip and the aar have a class file at their root"
        with('checkClassUniqueness').build()
        !new File(projectDir, 'baseline-class-uniqueness.lock').exists()
    }

    def 'checks the jars of projects on the compile classpath'() {
        when:
        multiProject.addSubproject('foo')
        multiProject.addSubproject('bar')
        file('foo/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int foo; }'
        file('bar/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int bar; }'

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':foo')
            api project(':bar')
        }
        checkClassUniqueness {
            configurations.add project.configurations.compileClasspath
        }
        """.stripIndent()

        then: "the compile classpath has the projects' classes directories, but the check indexes their jars"
        with('checkClassUniqueness').buildAndFail().output =~
                /## compileClasspath\s+\[\S+:bar, \S+:foo\]\s+- com\.something\.MyClass/
    }

    def 'checks the test fixtures jars of projects'() {
        when:
        writeProjectConflictingWithBar("testFixtures(project(':foo'))", "apply plugin: 'java-test-fixtures'", 'testFixtures')

        then:
        with('checkClassUniqueness').buildAndFail().output =~ FOO_AND_BAR_CONFLICT_ON_BOTH_CLASSPATHS
    }

    def 'checks jars that projects publish from other configurations'() {
        when:
        writeProjectConflictingWithBar("project(path: ':foo', configuration: 'extraJarElements')", '''
            def extraJar = tasks.register('extraJar', Jar) {
                archiveClassifier = 'extra'
                from sourceSets.main.output
            }
            configurations {
                extraJarElements {
                    canBeConsumed = true
                    canBeResolved = false
                }
            }
            artifacts {
                extraJarElements extraJar
            }
            '''.stripIndent())

        then:
        with('checkClassUniqueness').buildAndFail().output =~ FOO_AND_BAR_CONFLICT_ON_BOTH_CLASSPATHS
    }

    def 'checks the jars that replace the jars of projects'() {
        when:
        // The way projects publish a shadow jar instead of their own jar, with a plain Jar task standing in for the
        // shadow plugin's
        writeProjectConflictingWithBar("project(':foo')", '''
            def shadowJar = tasks.register('shadowJar', Jar) {
                archiveClassifier = 'all'
                from sourceSets.main.output
            }
            tasks.named('jar') {
                enabled = false
            }
            configurations {
                [apiElements, runtimeElements].each {
                    it.outgoing.artifacts.clear()
                    it.outgoing.artifact(shadowJar)
                }
            }
            '''.stripIndent())

        then:
        with('checkClassUniqueness').buildAndFail().output =~ FOO_AND_BAR_CONFLICT_ON_BOTH_CLASSPATHS
    }

    def 'indexes each jar once for projects with the same classpath'() {
        when:
        // a module name unique to this test run, so the jars aren't already indexed by an earlier test
        String module = "conflicting-${UUID.randomUUID()}"
        writeConflictingModules(module)
        String dependencies = """
        apply plugin: 'com.palantir.baseline-class-uniqueness'
        repositories {
            maven { url = uri('${new File(projectDir, 'repo').toURI()}') }
        }
        dependencies {
            implementation 'g:${module}:1.0'
            implementation 'g:${module}-copy:1.0'
        }
        """.stripIndent()
        multiProject.addSubproject('one', dependencies)
        multiProject.addSubproject('two', dependencies)
        buildFile << standardBuildFile

        BuildResult result = with('checkClassUniqueness', '--fix', '--info').build()

        then:
        String expected = lockFile('runtimeClasspath', "[g:${module}, g:${module}-copy]", FOO_CLASS)
        file('one/baseline-class-uniqueness.lock').text == expected
        file('two/baseline-class-uniqueness.lock').text == expected

        and: "the transforms cache shares an index between projects: Gradle logs this line for each run of the transform"
        result.output.findAll(/Caching disabled for ClassIndexTransform: \S+\/${module}-1\.0\.jar because/).size() == 1

        when:
        BuildResult again = with('checkClassUniqueness', '--rerun-tasks', '--info').build()

        then: "the transforms cache keeps the index for later builds, even though the transform isn't cacheable"
        again.output.findAll(/Caching disabled for ClassIndexTransform: \S+\/${module}-1\.0\.jar because/).isEmpty()
        again.task(':one:checkClassUniqueness').outcome == TaskOutcome.SUCCESS
    }

    def 'works with the configuration cache'() {
        File lockfile = new File(projectDir, 'baseline-class-uniqueness.lock')

        when:
        writeConflictingModules('conflicting')
        buildFile << standardBuildFile
        buildFile << """
        repositories {
            maven { url = uri('repo') }
        }
        dependencies {
            implementation 'g:conflicting:1.0'
            implementation 'g:conflicting-copy:1.0'
        }
        """.stripIndent()

        then:
        with('checkClassUniqueness', '--configuration-cache').buildAndFail().output
                .contains('baseline-class-uniqueness detected multiple jars containing identically named classes')
        with('checkClassUniqueness', '--configuration-cache', '--fix').build()

        when:
        BuildResult reused = with('checkClassUniqueness', '--configuration-cache').build()

        then:
        reused.output.contains('Configuration cache entry reused')
        reused.task(':checkClassUniqueness').outcome == TaskOutcome.UP_TO_DATE

        when:
        lockfile.text = lockfile.text.replace(FOO_CLASS, 'com.example.Bar')
        BuildResult outOfDate = with('checkClassUniqueness', '--configuration-cache').buildAndFail()

        then: "the check still runs on a reused entry"
        outOfDate.output.contains('Configuration cache entry reused')
        outOfDate.output.contains('baseline-class-uniqueness.lock is out of date')
    }

    @Unroll
    def '#gradleVersion: labels project jars from a reused configuration cache entry'() {
        when:
        multiProject.addSubproject('foo')
        multiProject.addSubproject('bar')
        file('foo/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int foo; }'
        file('bar/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int bar; }'
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':foo')
            api project(':bar')
        }
        """.stripIndent()
        with('checkClassUniqueness', '--configuration-cache').withGradleVersion(gradleVersion).buildAndFail()
        BuildResult reused = with('checkClassUniqueness', '--configuration-cache').withGradleVersion(gradleVersion).buildAndFail()

        then:
        reused.output.contains('Configuration cache entry reused')

        and: "the projects' coordinates come from the dependency graph, which the reused entry still resolves"
        reused.output =~ /\[\S+:bar, \S+:foo\]\s+- com\.something\.MyClass/

        where:
        gradleVersion << GradleTestVersions.gradleVersionsForTests
    }

    def 'skips jars that do not exist and checks the rest'() {
        when:
        multiProject.addSubproject('nojar', """
        tasks.named('jar') { enabled = false }
        """)
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':nojar')
            api group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
            api group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }
        """.stripIndent()

        then:
        with('checkClassUniqueness').buildAndFail().output.contains("[javax.el:javax.el-api, javax.servlet.jsp:jsp-api]")
    }

    private static String lockFile(String configuration, String jars, String className) {
        return """\
            # Danger! Multiple jars contain identically named classes. This may cause different behaviour depending on classpath ordering.
            # Run ./gradlew checkClassUniqueness --fix to update this file

            ## ${configuration}
            ${jars}
              - ${className}
            """.stripIndent()
    }

    /** Two modules, {@code g:<module>:1.0} and {@code g:<module>-copy:1.0}, with differing copies of one class. */
    private void writeConflictingModules(String module) {
        writeJar("repo/g/${module}/1.0/${module}-1.0.jar", [(FOO_CLASS_FILE): 'one'])
        writePom('g', module, '1.0', "repo/g/${module}/1.0/${module}-1.0.pom")
        writeJar("repo/g/${module}-copy/1.0/${module}-copy-1.0.jar", [(FOO_CLASS_FILE): 'two'])
        writePom('g', "${module}-copy", '1.0', "repo/g/${module}-copy/1.0/${module}-copy-1.0.pom")
    }

    private void writeJar(String path, Map<String, String> entries) {
        File jar = file(path)
        jar.parentFile.mkdirs()
        new ZipOutputStream(jar.newOutputStream()).withCloseable { zip ->
            entries.each { name, content ->
                zip.putNextEntry(new ZipEntry(name))
                zip.write(content.bytes)
                zip.closeEntry()
            }
        }
    }

    /**
     * Projects {@code foo} and {@code bar} with differing copies of {@code com.something.MyClass}, which the root
     * project depends on, {@code foo} through the given dependency. Both of its classpaths are checked.
     */
    private void writeProjectConflictingWithBar(String fooDependency, String fooBuildFile, String fooSourceSet = 'main') {
        multiProject.addSubproject('foo', fooBuildFile)
        multiProject.addSubproject('bar')
        file("foo/src/${fooSourceSet}/java/com/something/MyClass.java") << 'package com.something; class MyClass { int foo; }'
        file('bar/src/main/java/com/something/MyClass.java') << 'package com.something; class MyClass { int bar; }'
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api ${fooDependency}
            api project(':bar')
        }
        checkClassUniqueness {
            configurations.add project.configurations.compileClasspath
        }
        """.stripIndent()
    }

    /** {@code g:other:1.0}, whose copy of {@link #FOO_CLASS} differs from any other test's. */
    private void writeOtherModule() {
        writeJar('repo/g/other/1.0/other-1.0.jar', [(FOO_CLASS_FILE): 'other'])
        writePom('g', 'other', '1.0', 'repo/g/other/1.0/other-1.0.pom')
    }

    /** Writes a POM, with {@code elements} such as {@code <packaging>} or {@code <dependencies>} after the version. */
    private void writePom(String group, String module, String version, String path, String elements = '') {
        File pom = file(path)
        pom.parentFile.mkdirs()
        pom.text = """\
            <project><modelVersion>4.0.0</modelVersion><groupId>${group}</groupId><artifactId>${module}</artifactId>
            <version>${version}</version>${elements}</project>
            """.stripIndent()
    }
}
