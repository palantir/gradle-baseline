/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap
import com.palantir.baseline.util.GitUtils
import groovy.transform.CompileStatic
import groovy.xml.XmlUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.GenerateIdeaProject
import org.gradle.plugins.ide.idea.GenerateIdeaWorkspace
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.ModuleDependency

class BaselineIdea extends AbstractBaselinePlugin {

    static SAVE_ACTIONS_PLUGIN_MINIMUM_VERSION = '1.9.0'

    void apply(Project project) {
        this.project = project

        project.plugins.apply IdeaPlugin

        if (project == project.rootProject) {
            applyToRootProject(project)
        } else {
            // Be defensive - it never makes sense to apply this project to only a subproject but not to the root.
            project.rootProject.pluginManager.apply(BaselineIdea)
        }

        // Configure Idea module
        IdeaModel ideaModuleModel = project.extensions.getByType(IdeaModel)
        moveProjectReferencesToEnd(ideaModuleModel)

        // If someone renames a project, leftover {ipr,iml,ipr} files may still exist on disk and
        // confuse users, so we proactively clean them up. Intentionally using an Action<Task> to allow up-to-dateness.
        Action<Task> cleanup = new Action<Task>() {
            void execute(Task t) {
                if (t.project.rootProject == t.project) {
                    def iprFile = t.project.tasks.withType(GenerateIdeaProject).find().outputFile
                    def iwsFile = t.project.tasks.withType(GenerateIdeaWorkspace).find().outputFile
                    project.delete(project.fileTree(
                            dir: project.getProjectDir(), include: '*.ipr', exclude: isFile(iprFile)))
                    project.delete(project.fileTree(
                            dir: project.getProjectDir(), include: '*.iws', exclude: isFile(iwsFile)))
                }

                def imlFile = t.project.tasks.withType(GenerateIdeaModule).find().outputFile
                project.delete(project.fileTree(
                        dir: project.getProjectDir(), include: '*.iml', exclude: isFile(imlFile)))
            }
        }

        project.getTasks().findByName("idea").doLast(cleanup)
    }

    void applyToRootProject(Project project) {
        // Configure Idea project
        IdeaModel ideaRootModel = project.extensions.findByType(IdeaModel)
        ideaRootModel.project.ipr.withXml { provider ->
            Node node = provider.asNode()
            addCodeStyle(node)
            addCopyright(node)
            addCheckstyle(node)
            addEclipseFormat(node)
            addGit(node)
            addInspectionProjectProfile(node)
            addJavacSettings(node)
            addGitHubIssueNavigation(node)
            ignoreCommonShadedPackages(node)
        }
        configureProjectForIntellijImport(project)

        project.afterEvaluate {
            ideaRootModel.workspace.iws.withXml { provider ->
                Node node = provider.asNode()
                setRunManagerWorkingDirectory(node)
                addEditorSettings(node)
            }
        }

        // Suggest and configure the "save actions" plugin if Palantir Java Format is turned on.
        // This plugin can only be applied to the root project, and it applied as a side-effect of applying
        // 'com.palantir.java-format' to any subproject.
        project.getPluginManager().withPlugin("com.palantir.java-format-idea") {
            ideaRootModel.project.ipr.withXml { provider ->
                Node node = provider.asNode()
                configureSaveActions(node)
                configureExternalDependencies(node)
            }
            configureSaveActionsForIntellijImport(project)
        }
    }

    @CompileStatic
    static Spec<FileTreeElement> isFile(File file) {
        { FileTreeElement details -> details.file == file } as Spec<FileTreeElement>
    }

    private void configureProjectForIntellijImport(Project project) {
        if (!Boolean.getBoolean("idea.active")) {
            return
        }
        addCodeStyleIntellijImport()
        addCheckstyleIntellijImport(project)
        addCopyrightIntellijImport()
    }

    /**
     * Extracts IDEA formatting configurations from Baseline directory and adds it to the Idea project XML node.
     */
    private void addCodeStyle(node) {
        def ideaStyleFile = project.file("${configDir}/idea/intellij-java-palantir-style.xml")
        node.append(new XmlParser().parse(ideaStyleFile).component)
    }

    private void addCodeStyleIntellijImport() {
        def ideaStyleFile = project.file("${configDir}/idea/intellij-java-palantir-style.xml")

        def ideaStyle = new XmlParser().parse(ideaStyleFile)
                .component
                .find { it.'@name' == 'ProjectCodeStyleSettingsManager' }

        Node ideaStyleConfig = ideaStyle.option.find { it.'@name' == 'USE_PER_PROJECT_SETTINGS' }

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/codeStyles/codeStyleConfig.xml"),
                {
                    def state = GroovyXmlUtils.matchOrCreateChild(it, "state")
                    state.append(ideaStyleConfig)
                },
                {
                    new Node(null, "component", ImmutableMap.of("name", "ProjectCodeStyleConfiguration"))
                })


        def ideaStyleSettings = ideaStyle.option.find {it.'@name' == 'PER_PROJECT_SETTINGS'}

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/codeStyles/Project.xml"),
                {
                    def codeScheme = GroovyXmlUtils.matchOrCreateChild(it, "code_scheme", [name: 'Project'])
                    ideaStyleSettings.value.option.forEach {
                        codeScheme.append(it)
                    }
                },
                {
                    new Node(null, "component", ImmutableMap.of("name", "ProjectCodeStyleConfiguration"))
                })
    }

    /**
     * Extracts copyright headers from Baseline directory and adds them to Idea project XML node.
     */
    private void addCopyright(node) {
        def copyrightManager = node.component.find { it.'@name' == 'CopyrightManager' }
        def copyrightDir = Paths.get("${configDir}/copyright/")
        def copyrightFiles = getCopyrightFiles(copyrightDir)
        copyrightFiles.each { File file ->
            def fileName = copyrightDir.relativize(file.toPath())
            def copyrightNode = copyrightManager.copyright.find {
                it.option.find { it.@name == "myName" }?.@value == fileName
            }
            if (copyrightNode == null) {
                addCopyrightFile(copyrightManager, file, fileName.toString())
            }
        }

        def lastFileName = copyrightDir.relativize(copyrightFiles.iterator().toList().sort().last().toPath())
        copyrightManager.@default = lastFileName
    }

    private void addCopyrightIntellijImport() {
        def copyrightDir = Paths.get("${configDir}/copyright/")
        def copyrightFiles = getCopyrightFiles(copyrightDir)

        Supplier<Node> copyrightManagerNode = {
            return new Node(null, "component", ImmutableMap.of("name", "CopyrightManager"))
        }

        copyrightFiles.each { File file ->
            def fileName = copyrightDir.relativize(file.toPath()).toString()
            def extensionIndex = fileName.lastIndexOf(".")
            if (extensionIndex == -1) {
                extensionIndex = fileName.length()
            }
            def xmlFileName = fileName.substring(0, extensionIndex) + ".xml"

            XmlUtils.createOrUpdateXmlFile(
                    // Replace the extension by xml for the actual file
                    project.file(".idea/copyright/" + xmlFileName),
                    { node ->
                        addCopyrightFile(node, file, fileName)
                    },
                    copyrightManagerNode)
        }

        def lastFileName = copyrightDir.relativize(copyrightFiles.iterator().toList().sort().last().toPath())

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/copyright/profiles_settings.xml"),
                { node ->
                    node.append(new Node(null, "settings", ImmutableMap.of("default", lastFileName)))
                },
                copyrightManagerNode)
    }

    private PatternFilterable getCopyrightFiles(copyrightDir) {
        assert Files.exists(copyrightDir), "${copyrightDir} must exist"
        def copyrightFiles = project.fileTree(copyrightDir.toFile()).include("*")
        assert copyrightFiles.iterator().hasNext(), "${copyrightDir} must contain one or more copyright file"

        return copyrightFiles
    }

    private static void addCopyrightFile(node, File file, String fileName) {
        def copyrightText = XmlUtil.escapeControlCharacters(XmlUtil.escapeXml(file.text.trim()))
        node.append(new XmlParser().parseText("""
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

    private void addEclipseFormat(node) {
        def baselineFormat = project.plugins.findPlugin(BaselineFormat)
        if (baselineFormat == null) {
            project.logger.debug "Baseline: Skipping IDEA eclipse format configuration since baseline-format not applied"
            return
        }

        if (!BaselineFormat.eclipseFormattingEnabled(project)) {
            project.logger.debug "Baseline: Not configuring EclipseCodeFormatter because com.palantir.baseline-format.eclipse is not enabled in gradle.properties"
            return;
        }

        Path formatterConfig = BaselineFormat.eclipseConfigFile(project)
        if (!Files.exists(formatterConfig)) {
            project.logger.warn "Please run ./gradlew baselineUpdateConfig to create eclipse formatter config: " + formatterConfig
            return
        }

        project.logger.debug "Baseline: Configuring EclipseCodeFormatter plugin for Idea"
        node.append(new XmlParser().parseText("""
             <component name="EclipseCodeFormatterProjectSettings">
                <option name="projectSpecificProfile">
                  <ProjectSpecificProfile>
                    <option name="formatter" value="ECLIPSE" />
                    <option name="importOrder" value="" />
                    <option name="pathToConfigFileJava" value="\$PROJECT_DIR\$/.baseline/spotless/eclipse.xml" />
                    <option name="selectedJavaProfile" value="PalantirStyle" />
                  </ProjectSpecificProfile>
                </option>
              </component>
            """))
        def externalDependencies = GroovyXmlUtils.matchOrCreateChild(node, 'component', [name: 'ExternalDependencies'])
        GroovyXmlUtils.matchOrCreateChild(externalDependencies, 'plugin', [id: 'EclipseCodeFormatter'])
    }

    private void addCheckstyle(node) {
        project.plugins.withType(BaselineCheckstyle) {
            project.logger.debug "Baseline: Configuring Checkstyle for Idea"

            addCheckstyleNode(node)
            addCheckstyleExternalDependencies(node)
        }
    }

    private static void addCheckstyleIntellijImport(project) {
        project.plugins.withType(BaselineCheckstyle) {
            project.logger.debug "Baseline: Configuring Checkstyle for Idea"

            XmlUtils.createOrUpdateXmlFile(
                    project.file(".idea/checkstyle-idea.xml"),
                    BaselineIdea.&addCheckstyleNode)
            XmlUtils.createOrUpdateXmlFile(
                    project.file(".idea/externalDependencies.xml"),
                    BaselineIdea.&addCheckstyleExternalDependencies)
        }
    }

    private static void addCheckstyleNode(node) {
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
    }

    private static void addCheckstyleExternalDependencies(node) {
        def externalDependencies = GroovyXmlUtils.matchOrCreateChild(node, 'component', [name: 'ExternalDependencies'])
        GroovyXmlUtils.matchOrCreateChild(externalDependencies, 'plugin', [id: 'CheckStyle-IDEA'])
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

    private static void addInspectionProjectProfile(node) {
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

    private static void addJavacSettings(node) {
        node.append(new XmlParser().parseText("""
            <component name="JavacSettings">
                <option name="PREFER_TARGET_JDK_COMPILER" value="false" />
            </component>
            """.stripIndent()))
    }

    private static void addEditorSettings(node) {
        node.append(new XmlParser().parseText("""
            <component name="CodeInsightWorkspaceSettings">
                <option name="optimizeImportsOnTheFly" value="true" />
              </component>
            """.stripIndent()))
    }

    private static void addGitHubIssueNavigation(node) {
        GitUtils.maybeGitHubUri().ifPresent { githubUri ->
            node.append(new XmlParser().parseText("""
             <component name="IssueNavigationConfiguration">
               <option name="links">
                 <list>
                   <IssueNavigationLink>
                     <option name="issueRegexp" value="TODO.*\\#([0-9]+)" />
                     <option name="linkRegexp" value="${githubUri}/issues/\$1" />
                   </IssueNavigationLink>
                 </list>
               </option>
             </component>
            """.stripIndent()))
        }
    }

    private static void ignoreCommonShadedPackages(Node node) {
        // language=xml
        node.append(new XmlParser().parseText('''
            <component name="JavaProjectCodeInsightSettings">
              <excluded-names>
                <name>shadow</name><!-- from gradle-shadow-jar -->
                <name>org.gradle.internal.impldep</name>
                <name>autovalue.shaded</name>
                <name>org.inferred.freebuilder.shaded</name>
                <name>org.immutables.value.internal</name>
              </excluded-names>
            </component>
        '''.stripIndent()))
    }

    private static void configureSaveActionsForIntellijImport(Project project) {
        if (!Boolean.getBoolean("idea.active")) {
            return
        }
        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/externalDependencies.xml"),
                BaselineIdea.&configureExternalDependencies)
        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/saveactions_settings.xml"),
                BaselineIdea.&configureSaveActions)
    }

    /**
     * Configure the default working directory of RunManager configurations to be the module directory.
     */
    private static void setRunManagerWorkingDirectory(Node node) {
        def runTypes = ['Application', 'JUnit'] as Set

        def runManager = GroovyXmlUtils.matchOrCreateChild(node, 'component', [name: 'RunManager'])
        runTypes.each { runType ->
            def configuration = GroovyXmlUtils.matchOrCreateChild(runManager, 'configuration',
                    [default: 'true', type: runType],
                    [factoryName: runType])
            def workingDirectory = GroovyXmlUtils.matchOrCreateChild(configuration, 'option', [name: 'WORKING_DIRECTORY'])
            workingDirectory.'@value' = 'file://$MODULE_DIR$'
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
    private static void moveProjectReferencesToEnd(IdeaModel ideaModel) {
        ideaModel.module.iml.whenMerged { module ->
            def projectRefs = module.dependencies.findAll { it instanceof ModuleDependency }
            module.dependencies.removeAll(projectRefs)
            module.dependencies.addAll(projectRefs)
        }
    }

    /**
     * Configures some defaults on the save-actions plugin, but only if it hasn't been configured before.
     */
    private static void configureSaveActions(Node rootNode) {
        GroovyXmlUtils.matchOrCreateChild(rootNode, 'component', [name: 'SaveActionSettings'], [:]) {
            // Configure defaults if this plugin is configured for the first time only
            appendNode('option', [name: 'actions']).appendNode('set').with {
                appendNode('option', [value: 'activate'])
                appendNode('option', [value: 'noActionIfCompileErrors'])
                appendNode('option', [value: 'organizeImports'])
                appendNode('option', [value: 'reformat'])
            }
            appendNode('option', [name: 'configurationPath', value: ''])
            appendNode('option', [name: 'inclusions']).appendNode('set').with {
                appendNode('option', [value: "src${File.separator}.*\\.java"])
            }
        }
    }

    private static void configureExternalDependencies(Node rootNode) {
        def externalDependencies =
                GroovyXmlUtils.matchOrCreateChild(rootNode, 'component', [name: 'ExternalDependencies'])
        // I kid you not, this is the id for the save actions plugin:
        // https://github.com/dubreuia/intellij-plugin-save-actions/blob/v1.9.0/src/main/resources/META-INF/plugin.xml#L5
        // https://plugins.jetbrains.com/plugin/7642-save-actions/
        GroovyXmlUtils.matchOrCreateChild(
                externalDependencies,
                'plugin',
                [id: 'com.dubreuia'],
                ['min-version': SAVE_ACTIONS_PLUGIN_MINIMUM_VERSION])
    }
}
