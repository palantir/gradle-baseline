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

import java.nio.file.Files
import java.util.stream.Stream
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GFileUtils

class BaselineClassUniquenessPluginIntegrationTest extends AbstractPluginTest {

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

    def 'detect duplicates in two external jars, then --fix captures'() {
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
        with("checkClassUniqueness", "--fix").build()

        then:
        lockfile.exists()
        File expected = new File("src/test/resources/com/palantir/baseline/baseline-class-uniqueness.expected.lock")
        if (Boolean.getBoolean("recreate")) {
            GFileUtils.writeFile(lockfile.text, expected)
        }
        lockfile.text == expected.text
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
            GFileUtils.writeFile(lockfile.text, expected)
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
            GFileUtils.writeFile(lockfile.text, expected)
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

    def 'currently skips duplicates from user-authored code'() {
        when:
        Stream.of(multiProject.addSubproject('foo'), multiProject.addSubproject('bar')).forEach({ subproject ->
            File myClass = new File(subproject, "src/main/com/something/MyClass.java")
            Files.createDirectories(myClass.toPath().getParent())
            myClass << "package com.something; class MyClass {}"
        })

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            api project(':foo')
            api project(':bar')
        }
        """.stripIndent()

        then:
        BuildResult result = with('checkClassUniqueness', '--info').build()
        println result.getOutput()
        result.task(":checkClassUniqueness").outcome == TaskOutcome.SUCCESS // ideally should should say failed!
    }
}
