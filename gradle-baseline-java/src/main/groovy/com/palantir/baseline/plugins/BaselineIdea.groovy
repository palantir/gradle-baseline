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
import java.nio.file.Files
import java.nio.file.Paths
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

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
                    addInspectionProjectProfile(node)
                }

                ideaRootModel.workspace.iws.withXml { provider ->
                    def node = provider.asNode()
                    setRunManagerWorkingDirectory(node)
                }
            }

            // Configure Idea module
            IdeaModel ideaModuleModel = project.extensions.findByType(IdeaModel)
            addJdkVersion(ideaModuleModel)
            markResourcesDirs(ideaModuleModel);
            moveProjectReferencesToEnd(ideaModuleModel);
        }

        // If someone renames a project, leftover {ipr,iml,ipr} files may still exist on disk and
        // confuse users, so we proactively clean them up. Intentionally using an Action<Task> to allow up-to-dateness.
        Action<Task> cleanup = new Action<Task>() {
            void execute(Task t) {
                project.delete(project.fileTree(
                        dir: project.getProjectDir(), include: '*.ipr', exclude: "${project.name}.ipr"))
                project.delete(project.fileTree(
                        dir: project.getProjectDir(), include: '*.iml', exclude: "${project.name}.iml"))
                project.delete(project.fileTree(
                        dir: project.getProjectDir(), include: '*.iws', exclude: "${project.name}.iws"))
            }
        }

        project.getTasks().findByName("idea").doLast(cleanup)
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
        assert Files.exists(copyrightDir), "${copyrightDir} must exist"
        def copyrightFiles = project.fileTree(copyrightDir.toFile()).include("*")
        assert copyrightFiles.iterator().hasNext(), "${copyrightDir} must contain one or more copyright file"
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

        def lastFileName = copyrightDir.relativize(copyrightFiles.iterator().toList().sort().last().toPath())
        copyrightManager.@default = lastFileName
    }

    private void addCheckstyle(node) {
        def checkstyle = project.plugins.findPlugin(BaselineCheckstyle)
        if (checkstyle == null) {
            project.logger.debug "Baseline: Skipping IDEA checkstyle configuration since baseline-checkstyle not applied"
            return
        }

        project.logger.debug "Baseline: Configuring Checkstyle for Idea"
        def checkstyleFile = "LOCAL_FILE:\$PROJECT_DIR\$/.baseline/checkstyle/checkstyle.xml"
        node.append(new XmlParser().parseText("""
            <component name="CheckStyle-IDEA">
              <option name="configuration">
                <map>
                  <entry key="active-configuration" value="${checkstyleFile}:Baseline Checkstyle" />
                  <entry key="checkstyle-version" value="${BaselineCheckstyle.DEFAULT_CHECKSTYLE_VERSION}" />
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

        def externalDependencies = matchOrCreateChild(node, 'component', [name: 'ExternalDependencies'])
        matchOrCreateChild(externalDependencies, 'plugin', [id: 'CheckStyle-IDEA'])
    }

    /**
     * Enables Git support for the given project configuration.
     */
    private void addGit(node) {
        if (!project.file(".git").isDirectory()) {
            project.logger.debug "Baseline: Skipping IDEA Git configuration since .git directory does not exist."
            return
        }

        node.append(new XmlParser().parseText('''
            <component name="VcsDirectoryMappings">
                <mapping directory="$PROJECT_DIR$" vcs="Git" />
            </component>
            '''.stripIndent()))
    }

    private void addInspectionProjectProfile(node) {
        node.append(new XmlParser().parseText("""
            <component name="InspectionProjectProfileManager">
                <profile version="1.0">
                    <inspection_tool class="MissingOverrideAnnotation" enabled="true" level="WARNING" enabled_by_default="true">
                        <option name="ignoreObjectMethods" value="false" />
                        <option name="ignoreAnonymousClassMethods" value="false" />
                    </inspection_tool>
                </profile>
                <option name="PROJECT_PROFILE" value="Default" />
                <option name="USE_PROJECT_PROFILE" value="true" />
            </component>
            """.stripIndent()))
    }

    /**
     * Configure the default working directory of RunManager configurations to be the module directory.
     */
    private static void setRunManagerWorkingDirectory(Node node) {
        def runTypes = ['Application', 'JUnit'] as Set

        def runManager = matchOrCreateChild(node, 'component', [name: 'RunManager'])
        runTypes.each { runType ->
            def configuration = matchOrCreateChild(runManager, 'configuration',
                    [default: 'true', type: runType],
                    [factoryName: runType])
            def workingDirectory = matchOrCreateChild(configuration, 'option', [name: 'WORKING_DIRECTORY'])
            workingDirectory.'@value' = 'file://$MODULE_DIR$'
        }
    }

    private static Node matchOrCreateChild(Node base, String name, Map attributes = [:], Map defaults = [:]) {
        def child = base[name].find { it.attributes().entrySet().containsAll(attributes.entrySet()) }
        if (child) {
            return child
        }

        return base.appendNode(name, attributes + defaults)
    }

    /**
     * Configures JDK and Java language level of the given IdeaModel according to the sourceCompatibility property.
     */
    private void addJdkVersion(IdeaModel ideaModel) {
        def compileJavaTask = (JavaCompile) project.tasks.findByName('compileJava')
        if (compileJavaTask) {
            def javaVersion = compileJavaTask.sourceCompatibility
            def jdkVersion = 'JDK_' + javaVersion.replaceAll('\\.', '_')
            project.logger.debug("BaselineIdea: Configuring IDEA Module for Java version: " + javaVersion)

            if (ideaModel.project != null) {
                ideaModel.project.languageLevel = javaVersion
            }

            ideaModel.module.jdkName = javaVersion
            ideaModel.module.iml.withXml {
                it.asNode().component.find { it.@name == 'NewModuleRootManager' }.@LANGUAGE_LEVEL = jdkVersion
            }
        } else {
            project.logger.debug("BaselineIdea: No Java version found in sourceCompatibility property.")
        }
    }

    /**
     * By default the Idea plugin marks resources dirs as source dirs.
     */
    private void markResourcesDirs(IdeaModel ideaModel) {
        ideaModel.module.iml.withXml {
            def node = it.asNode()
            def content = node.component.find { it.'@name' == 'NewModuleRootManager' }.content[0]
            content.sourceFolder.each { sourceFolder ->
                if(sourceFolder.@url?.endsWith('/resources')) {
                    sourceFolder.attributes().with {
                        boolean isTestSource = (remove('isTestSource') == 'true')
                        put('type', isTestSource ? 'java-test-resource' : 'java-resource')
                    }
                }
            }
        }
    }

    /**
     * By default, IntelliJ and Gradle have different classpath behaviour with subprojects.
     *
     * Suppose that project A depends on project B, and A depends on foo 2.0, and B depends on foo 1.0.
     *
     * In Gradle, the classpath for project A will contain foo 2.0 (assuming default resolution strategy),
     * whilst the classpath for project B contains foo 1.0.
     *
     * In IntelliJ under probable settings, the classpath for project A contains project B's classpath,
     * which is likely at the top of the classpath. This means that foo 1.0 appears in the classpath
     * before foo 2.0, leading to issues.
     *
     * This moves all project references to the end of the dependencies list, which unifies behaviour
     * between Gradle and IntelliJ.
     */
    private void moveProjectReferencesToEnd(IdeaModel ideaModel) {
        ideaModel.module.iml.whenMerged { module ->
            def projectRefs = module.dependencies.findAll { it instanceof org.gradle.plugins.ide.idea.model.ModuleDependency }
            module.dependencies.removeAll(projectRefs)
            module.dependencies.addAll(projectRefs)
        }
    }
}
