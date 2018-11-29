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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineVersionsIntegrationTest extends AbstractPluginTest {

    def bomVersion = "42.42.42"

    def standardBuildFile(File projectDir) {
        """
        plugins {
            id 'java'
            id 'com.palantir.baseline-versions'
        }
    
        repositories {
            mavenCentral()
            maven { url "file://${projectDir.absolutePath}/maven" }
        }
        
        dependencies {
            compile 'org.scala-lang:scala-library'
        }
        
        dependencyRecommendations {
            mavenBom module: 'com.palantir.product:your-bom:${bomVersion}'
        }
        """.stripIndent()
    }

    static String pomWithJarPackaging(String group, String artifact, String version) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <modelVersion>4.0.0</modelVersion>
            <groupId>$group</groupId>
            <artifactId>$artifact</artifactId>
            <packaging>jar</packaging>
            <version>$version</version>
            <description/>
            <dependencies/>
            </project>
        """.stripIndent()
    }

    def setup() {
        String bomContent =
            """<?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.palantir.product</groupId>
                  <artifactId>your-bom</artifactId>
                  <version>42.42.42</version>
                  <dependencies>
                  </dependencies>
                  <name>your-bom</name>
                  <description/>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.palantir.product</groupId>
                        <artifactId>your-bom</artifactId>
                        <version>${bomVersion}</version>
                      </dependency>
                      <dependency>
                        <groupId>org.scala-lang</groupId>
                        <artifactId>scala-library</artifactId>
                        <version>2.12.5</version>
                      </dependency>
                      <dependency>
                        <groupId>org.scala-lang</groupId>
                        <artifactId>scala-compiler</artifactId>
                        <version>2.12.5</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
            """.stripIndent()
        Path bomPath = Paths.get(projectDir.toString(), "maven", "com", "palantir", "product", "your-bom", bomVersion)
        Files.createDirectories(bomPath)
        Files.write(bomPath.resolve("your-bom-${bomVersion}.pom"), bomContent.getBytes(StandardCharsets.UTF_8))
    }

    def setupVersionsProps(String propsContent) {
        Files.write(projectDir.toPath().resolve("versions.props"), propsContent.getBytes(StandardCharsets.UTF_8))
    }

    def buildSucceed() {
        BuildResult result = with('--info', 'checkVersionsProps').build()
        result.task(':checkVersionsProps').outcome == TaskOutcome.SUCCESS
        result
    }

    void buildAndFailWith(String error) {
        BuildResult result = with('checkVersionsProps', '--stacktrace').buildAndFail()
        assert result.output.contains(error)
    }

    def buildWithFixWorks() {
        def currentVersionsProps = file('versions.props').readLines()
        // Check that running with --fix modifies the file
        with('checkVersionsProps', '--fix').build()
        assert file('versions.props').readLines() != currentVersionsProps

        // Check that the task now succeeds
        with('checkVersionsProps').build()
    }

    def 'checkVersionsProps does not resolve artifacts'() {
        setupVersionsProps("")
        buildFile << """
            plugins {
                id 'java'
                id 'com.palantir.baseline-versions'
            }
        
            repositories {
                maven { url "${projectDir.toURI()}/maven" }
            }
            dependencies {
                compile 'com.palantir.product:foo:1.0.0'
            }
        """.stripIndent()

        // We're not producing a jar for this dependency, so artifact resolution would fail
        file('maven/com/palantir/product/foo/1.0.0/foo-1.0.0.pom') <<
                pomWithJarPackaging("com.palantir.product", "foo", "1.0.0")

        expect:
        buildSucceed()
    }

    def 'Override version conflict should succeed'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.6")
        buildFile << standardBuildFile(projectDir)

        then:
        def result = buildSucceed()
        result.output.contains("There are conflicts between versions.props and the bom:\n" +
                "  versions.props: org.scala-lang:scala-library -> 2.12.6\n" +
                "  bom:            org.scala-lang:scala-library -> 2.12.5")
    }

    def 'Trivially passes check on multiple projects'() {
        buildFile << """
            plugins {
                id 'com.palantir.baseline-versions'
            }
            allprojects {
                apply plugin: 'com.palantir.baseline-versions'
            }
        """.stripIndent()

        multiProject.addSubproject('foo', "apply plugin: 'java'")

        expect:
        with('check').build()
    }

    def 'Task should run as part of :check'() {
        when:
        buildFile << standardBuildFile(projectDir)

        then:
        def result = with('check', '--stacktrace').build()
        result.task(':checkVersionsProps').outcome == TaskOutcome.SUCCESS
    }

    def 'Same version conflict should fail'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.5")
        buildFile << standardBuildFile(projectDir)

        then:
        buildAndFailWith("Critical conflicts between versions.props and the bom (overriding with same version)")
        buildWithFixWorks()
    }

    def 'Same version conflict but wildcard override at least one should succeed'() {
        when:
        setupVersionsProps("org.scala-lang:scala-* = 2.12.5")
        buildFile << standardBuildFile(projectDir)
        buildFile << """
        dependencies {
            compile 'org.scala-lang:scala-reflect'
            compile 'org.scala-lang:scala-compiler'
        }"""

        then:
        def result = buildSucceed()
        result.output.contains("pin required by other non recommended artifacts: [org.scala-lang:scala-reflect]")
        result.output.contains("  bom:\n" +
                "                  org.scala-lang:scala-compiler -> 2.12.5\n" +
                "                  org.scala-lang:scala-library -> 2.12.5")
    }

    def 'Version props conflict should succeed'() {
        when:
        setupVersionsProps("""
            com.fasterxml.jackson.*:* = 2.9.3
            com.fasterxml.jackson.core:jackson-annotations = 2.9.5
        """.stripIndent())
        buildFile << standardBuildFile(projectDir)
        buildFile << """
        dependencies {
            compile 'com.fasterxml.jackson.core:jackson-databind'
        }""".stripIndent()

        then:
        buildSucceed()
    }

    def 'Last matching version should win'() {
        when:
        setupVersionsProps("""
            org.slf4j:slf4j-api = 1.7.25
            org.slf4j:* = 1.7.20
        """.stripIndent())
        buildFile << standardBuildFile(projectDir)
        buildFile << """
        dependencies {
            compile 'org.slf4j:slf4j-api'
        }""".stripIndent()

        then:
        buildAndFailWith('There are unused pins in your versions.props: \n[org.slf4j:slf4j-api]')
        buildWithFixWorks()
        file('versions.props').text.trim() == "org.slf4j:* = 1.7.20"
    }

    def 'Unused version should fail'() {
        when:
        setupVersionsProps("notused:atall = 42.42")
        buildFile << standardBuildFile(projectDir)

        then:
        buildAndFailWith("There are unused pins in your versions.props")
        buildWithFixWorks()
    }

    def 'recommending bom version shouldn\'t fail even if bom recommends itself'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.6\ncom.palantir.product:your-bom = ${bomVersion}")
        buildFile <<
        """
        plugins {
            id 'java'
            id 'com.palantir.baseline-versions'
        }

        repositories {
            mavenCentral()
            maven { url "file://${projectDir.absolutePath}/maven" }
        }

        dependencies {
            compile 'org.scala-lang:scala-library'
        }

        dependencyRecommendations {
            mavenBom module: 'com.palantir.product:your-bom'
        }
        """.stripIndent()

        then:
        buildSucceed()
    }

    def 'Unused check should use exact matching'() {
        when:
        setupVersionsProps("""
            com.google.guava:guava-testlib = 23.0
            com.google.guava:guava = 22.0
        """.stripIndent())
        buildFile << standardBuildFile(projectDir)
        buildFile << """
        dependencies {
            compile 'com.google.guava:guava'
            compile 'com.google.guava:guava-testlib'
        }""".stripIndent()

        then:
        buildSucceed()
    }

}
