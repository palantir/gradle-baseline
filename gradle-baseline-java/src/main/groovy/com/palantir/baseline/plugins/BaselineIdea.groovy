/*
 * Copyright 2015 Palantir Technologies, Inc.
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

package com.palantir.baseline.plugins

import groovy.xml.XmlUtil
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

import java.nio.file.Paths

class BaselineIdea extends AbstractBaselinePlugin {

    void apply(Project project) {
        this.project = project

        project.plugins.apply IdeaPlugin
        project.afterEvaluate {

            // Configure Idea project
            IdeaModel ideaRootModel = project.rootProject.extensions.findByType(IdeaModel)
            if (ideaRootModel) {
                ideaRootModel.project.ipr.withXml { provider ->
                    def node = provider.asNode()
                    addCodeStyle(node)
                    addCopyright(node)
                    addCheckstyle(node)
                    addGit(node)
                    addJUnitWorkingDirectory(node)
                }
            }

            // Configure Idea module
            IdeaModel ideaModuleModel = project.extensions.findByType(IdeaModel)
            addJdkVersion(ideaModuleModel)
        }
    }

    /**
     * Extracts IDEA formatting configurations from Baseline directory and adds it to the Idea project XML node.
     */
    private void addCodeStyle(node) {
        def ideaStyleFile = project.file("${configDir}/idea/intellij-java-palantir-style.xml")
        node.append(new XmlParser().parse(ideaStyleFile).component)
    }

    /**
     * Extracts copyright headers from Baseline directory and adds them to Idea project XML node.
     */
    private void addCopyright(node) {
        def copyrightManager = node.component.find { it.'@name' == 'CopyrightManager' }
        def copyrightDir = Paths.get("${configDir}/copyright/")
        def copyrightFiles = project.fileTree(copyrightDir.toFile()).include("*")
        copyrightFiles.each { File file ->
            def fileName = copyrightDir.relativize(file.toPath())
            def copyrightNode = copyrightManager.copyright.find {
                it.option.find { it.@name == "myName" }?.@value == fileName
            }
            if (copyrightNode == null) {
                    def copyrightText = XmlUtil.escapeControlCharacters(XmlUtil.escapeXml(file.text.trim()))
                    copyrightManager.append(new XmlParser().parseText("""
                        <copyright>
                            <option name="notice" value="${copyrightText}" />
                            <option name="keyword" value="Copyright" />
                            <option name="allowReplaceKeyword" value="" />
                            <option name="myName" value="${fileName}" />
                            <option name="myLocal" value="true" />
                        </copyright>
                        """.stripIndent()
                    ))
            }
        }

        def lastfileName = copyrightDir.relativize(copyrightFiles.iterator().toList().last().toPath())
        copyrightManager.@default = lastfileName
    }

    private void addCheckstyle(node) {
        def checkstyle = project.plugins.findPlugin(BaselineCheckstyle)
        if (checkstyle == null) {
            project.logger.info "Baseline: Skipping IDEA checkstyle configuration since baseline-checkstyle not applied"
            return
        }

        project.logger.info "Baseline: Configuring Checkstyle for Idea"
        def checkstyleFile = "LOCAL_FILE:\$PRJ_DIR\$.baseline/checkstyle/checkstyle.xml"
        node.append(new XmlParser().parseText("""
            <component name="CheckStyle-IDEA">
              <option name="configuration">
                <map>
                  <entry key="active-configuration" value="${checkstyleFile}:Baseline Checkstyle" />
                  <entry key="check-nonjava-files" value="false" />
                  <entry key="check-test-classes" value="true" />
                  <entry key="location-0" value="${checkstyleFile}:Baseline Checkstyle" />
                  <entry key="suppress-errors" value="false" />
                  <entry key="thirdparty-classpath" value="" />
                  <entry key="property-0.samedir" value="\$PROJECT_DIR\$/.baseline/checkstyle/" />
                </map>
              </option>
            </component>
            """.stripIndent()))
    }

    /**
     * Enables Git support for the given project configuration.
     */
    private void addGit(node) {
        if (!project.file(".git").isDirectory()) {
            project.logger.info "Baseline: Skipping IDEA Git configuration since .git directory does not exist."
            return
        }

        node.append(new XmlParser().parseText('''
            <component name="VcsDirectoryMappings">
                <mapping directory="$PROJECT_DIR$" vcs="Git" />
            </component>
            '''.stripIndent()))
    }

    /**
     * Configure the default working directory of JUnit tests to be the module directory.
     */
    private void addJUnitWorkingDirectory(node) {
        node.append(new XmlParser().parseText('''
            <component name="RunManager">
                <configuration default="true" type="JUnit" factoryName="JUnit">
                    <option name="WORKING_DIRECTORY" value="file://$MODULE_DIR$" />
                </configuration>
            </component>
            '''.stripIndent()))
    }

    /**
     * Configures JDK and Java language level of the given IdeaModel according to the sourceCompatibility property.
     */
    private void addJdkVersion(IdeaModel ideaModel) {
        def compileJavaTask = (JavaCompile) project.tasks.findByName('compileJava')
        if (compileJavaTask) {
            def javaVersion = compileJavaTask.sourceCompatibility
            def jdkVersion = 'JDK_' + javaVersion.replaceAll('\\.', '_')
            project.logger.info("BaselineIdea: Configuring IDEA Module for Java version: " + javaVersion)

            if (ideaModel.project != null) {
                ideaModel.project.languageLevel = javaVersion
            }

            ideaModel.module.jdkName = javaVersion
            ideaModel.module.iml.withXml {
                it.asNode().component.find { it.@name == 'NewModuleRootManager' }.@LANGUAGE_LEVEL = jdkVersion
            }
        } else {
            project.logger.info("BaselineIdea: No Java version found in sourceCompatibility property.")
        }
    }
}
