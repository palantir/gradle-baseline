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
import com.palantir.baseline.IntellijSupport
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionExtension
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionsExtension
import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion
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
import org.gradle.api.XmlProvider
import org.gradle.api.file.FileTreeElement
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.GenerateIdeaProject
import org.gradle.plugins.ide.idea.GenerateIdeaWorkspace
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.ModuleDependency

// TODO(dfox): separate the xml manipulation (which really benefits from groovy syntax) from typed things
//@CompileStatic
class BaselineIdea extends AbstractBaselinePlugin {

    static SAVE_ACTIONS_PLUGIN_MINIMUM_VERSION = '1.9.0'

    void apply(Project project) {
        this.project = project

        project.plugins.apply IdeaPlugin

        if (project == project.rootProject) {
            applyToRootProject(project)
        }

        // Configure Idea module
        IdeaModel ideaModuleModel = project.extensions.getByType(IdeaModel)
        moveProjectReferencesToEnd(ideaModuleModel)
        updateModuleLanguageVersion(ideaModuleModel, project)

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

        project.getTasks().named("idea").configure(idea -> idea.doLast(cleanup))
    }

    void applyToRootProject(Project rootProject) {
        // Configure Idea project
        IdeaModel ideaRootModel = rootProject.extensions.findByType(IdeaModel)
        ideaRootModel.project.ipr.withXml {XmlProvider provider ->
            Node node = provider.asNode()
            addCodeStyle(node)
            setRootJavaVersions(node)
            addCopyright(node)
            addCheckstyle(node)
            addEclipseFormat(node)
            addGit(node)
            addInspectionProjectProfile(node)
            addJavacSettings(node)
            addGitHubIssueNavigation(node)
            addExcludedAutoImports(node)
        }
        configureProjectForIntellijImport(rootProject)

        rootProject.afterEvaluate {
            ideaRootModel.workspace.iws.withXml {XmlProvider provider ->
                Node node = provider.asNode()
                setRunManagerWorkingDirectory(node)
                addEditorSettings(node)
            }
        }

        // Suggest and configure the "save actions" plugin if Palantir Java Format is turned on.
        // This plugin can only be applied to the root project, and it applied as a side-effect of applying
        // 'com.palantir.java-format' to any subproject.
        rootProject.getPluginManager().withPlugin("com.palantir.java-format-idea") {
            ideaRootModel.project.ipr.withXml {XmlProvider provider ->
                Node node = provider.asNode()
                configureSaveActions(node)
                configureExternalDependencies(node)
            }
            configureSaveActionsForIntellijImport(rootProject)
        }
    }

    @CompileStatic
    static Spec<FileTreeElement> isFile(File file) {
        {FileTreeElement details -> details.file == file} as Spec<FileTreeElement>
    }

    private void configureProjectForIntellijImport(Project project) {
        if (IntellijSupport.isRunningInIntellij()) {
            addCodeStyleIntellijImport()
            addCheckstyleIntellijImport(project)
            addCopyrightIntellijImport()
        }
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
        // This runs eagerly, so the file might not exist if we haven't run `baselineUpdateConfig` yet.
        // Thus, don't do anything if the file is not there yet.
        if (!ideaStyleFile.isFile()) {
            return
        }

        def ideaStyle = new XmlParser().parse(ideaStyleFile)
                .component
                .find {it.'@name' == 'ProjectCodeStyleSettingsManager'}

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/codeStyles/codeStyleConfig.xml"),
                {
                    def state = GroovyXmlUtils.matchOrCreateChild(it, "state")
                    def perProjectSettings = GroovyXmlUtils.matchOrCreateChild(
                            state, "option", [name: 'USE_PER_PROJECT_SETTINGS'])
                    perProjectSettings.attributes().'value' = "true"
                },
                {
                    new Node(null, "component", ImmutableMap.of("name", "ProjectCodeStyleConfiguration"))
                })


        def ideaStyleSettings = ideaStyle.option.find {it.'@name' == 'PER_PROJECT_SETTINGS'}

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/codeStyles/Project.xml"),
                {
                    def codeScheme = GroovyXmlUtils.matchOrCreateChild(it, "code_scheme", [name: 'Project'])
                    codeScheme.attributes().putIfAbsent("version", 173)
                    def javaCodeStyleSettings = GroovyXmlUtils.matchOrCreateChild(codeScheme, "JavaCodeStyleSettings")
                    // Avoid re-adding duplicate options to the project. This allows users to override settings based
                    // on preference.
                    ideaStyleSettings.value.option.forEach { ideaStyleSetting ->
                        def settingName = ideaStyleSetting.attributes().get("name")
                        if (settingName != null && javaCodeStyleSettings["option"].find { it.attributes().get("name") == settingName } == null) {
                            javaCodeStyleSettings.append(ideaStyleSetting)
                        }
                    }
                },
                {
                    new Node(null, "component", ImmutableMap.of("name", "ProjectCodeStyleConfiguration"))
                })
    }

    private void setRootJavaVersions(Node node) {
        BaselineJavaVersionsExtension versions = project.getExtensions().findByType(BaselineJavaVersionsExtension.class)
        if (versions != null) {
            updateCompilerConfiguration(node, versions)
            updateProjectRootManager(node, versions)
        }
    }

    private void updateCompilerConfiguration(Node node, BaselineJavaVersionsExtension versions) {
        Node compilerConfiguration = node.component.find { it.'@name' == 'CompilerConfiguration' }
        Node bytecodeTargetLevel = GroovyXmlUtils.matchOrCreateChild(compilerConfiguration, "bytecodeTargetLevel")
        JavaLanguageVersion defaultBytecodeVersion = versions.libraryTarget().get()
        bytecodeTargetLevel.attributes().put("target", defaultBytecodeVersion.toString())
        project.allprojects.forEach({ project ->
            BaselineJavaVersionExtension version = project.getExtensions().findByType(BaselineJavaVersionExtension.class)
            if (version != null && version.target().get().javaLanguageVersion().asInt() != defaultBytecodeVersion.asInt()) {
                bytecodeTargetLevel.appendNode("module", ImmutableMap.of(
                        "name", project.getName(),
                        "target", version.target().get().toString()))
            }
        })
    }

    private void updateProjectRootManager(Node node, BaselineJavaVersionsExtension versions) {
        Node projectRootManager = node.component.find { it.'@name' == 'ProjectRootManager' }
        ChosenJavaVersion chosenJavaVersion = versions.distributionTarget().get()
        int featureRelease = chosenJavaVersion.javaLanguageVersion().asInt()
        projectRootManager.attributes().put("project-jdk-name", featureRelease)
        projectRootManager.attributes().put("languageLevel", chosenJavaVersion.asIdeaLanguageLevel())
    }

    private static void updateModuleLanguageVersion(IdeaModel ideaModel, Project currentProject) {
        ideaModel.module.iml.withXml { XmlProvider provider ->
            // Extension must be checked lazily within the transformer
            BaselineJavaVersionExtension versionExtension = currentProject.extensions.findByType(BaselineJavaVersionExtension.class)
            if (versionExtension != null) {
                ChosenJavaVersion chosenJavaVersion = versionExtension.target().get()
                Node node = provider.asNode()
                Node newModuleRootManager = node.component.find { it.'@name' == 'NewModuleRootManager' }
                newModuleRootManager.attributes().put("LANGUAGE_LEVEL", chosenJavaVersion.asIdeaLanguageLevel())
            }
        }
    }

    /**
     * Extracts copyright headers from Baseline directory and adds them to Idea project XML node.
     */
    private void addCopyright(Node node) {
        Node copyrightManager = node.component.find {it.'@name' == 'CopyrightManager'}
        def copyrightDir = Paths.get("${configDir}/copyright/")
        def copyrightFiles = getCopyrightFiles(copyrightDir)
        copyrightFiles.each {File file ->
            def fileName = copyrightDir.relativize(file.toPath())
            def copyrightNode = copyrightManager.copyright.find {
                it.option.find {it.@name == "myName"}?.@value == fileName
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

        copyrightFiles.each {File file ->
            def fileName = copyrightDir.relativize(file.toPath()).toString()
            def extensionIndex = fileName.lastIndexOf(".")
            if (extensionIndex == -1) {
                extensionIndex = fileName.length()
            }
            def xmlFileName = fileName.substring(0, extensionIndex) + ".xml"

            XmlUtils.createOrUpdateXmlFile(
                    // Replace the extension by xml for the actual file
                    project.file(".idea/copyright/" + xmlFileName),
                    {node ->
                        createOrUpdateCopyrightFile(node, file, fileName)
                    },
                    copyrightManagerNode)
        }

        def lastFileName = copyrightDir.relativize(copyrightFiles.iterator().toList().sort().last().toPath())

        XmlUtils.createOrUpdateXmlFile(
                project.file(".idea/copyright/profiles_settings.xml"),
                {node ->
                    GroovyXmlUtils.matchOrCreateChild(node, "settings").attributes().'default' = lastFileName
                },
                copyrightManagerNode)
    }

    private PatternFilterable getCopyrightFiles(copyrightDir) {
        assert Files.exists(copyrightDir), "${copyrightDir} must exist"
        def copyrightFiles = project.fileTree(copyrightDir.toFile()).include("*")
        assert copyrightFiles.iterator().hasNext(), "${copyrightDir} must contain one or more copyright file"

        return copyrightFiles
    }

    private static void addCopyrightFile(Node node, File file, String fileName) {
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

    private static void createOrUpdateCopyrightFile(Node node, File file, String fileName) {
        def copyrightText = file.text.trim()
        // Ensure that subsequent runs don't produce duplicate entries
        Node copyrightNode = GroovyXmlUtils.matchOrCreateChild(node, "copyright")
        Node noticeNode = GroovyXmlUtils.matchOrCreateChild(copyrightNode, "option", ["name": "notice"])
        // Update the copyright text if it has changed
        noticeNode.attributes().put("value", copyrightText)
        GroovyXmlUtils.matchOrCreateChild(copyrightNode, "option", ["name": "keyword"], ["value": "Copyright"])
        GroovyXmlUtils.matchOrCreateChild(copyrightNode, "option", ["name": "allowReplaceKeyword"], ["value": ""])
        GroovyXmlUtils.matchOrCreateChild(copyrightNode, "option", ["name": "myName"], ["value": fileName])
        GroovyXmlUtils.matchOrCreateChild(copyrightNode, "option", ["name": "myLocal"], ["value": true])
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
            project.logger.warn "Please run ./gradlew baselineUpdateConfig to create eclipse formatter config: " +
                    formatterConfig
            return
        }

        project.logger.debug "Baseline: Configuring EclipseCodeFormatter plugin for Idea"
        // language=xml
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

    private void addCheckstyle(Node node) {
        project.plugins.withType(BaselineCheckstyle) {
            project.logger.debug "Baseline: Configuring Checkstyle for Idea"

            addCheckstyleNode(node)
            addCheckstyleExternalDependencies(node)
        }
    }

    private void addCheckstyleIntellijImport(Project project) {
        project.plugins.withType(BaselineCheckstyle) {
            project.logger.debug "Baseline: Configuring Checkstyle for Idea"

            XmlUtils.createOrUpdateXmlFile(
                    project.file(".idea/checkstyle-idea.xml"),
                    { addCheckstyleNode(it) })
            XmlUtils.createOrUpdateXmlFile(
                    project.file(".idea/externalDependencies.xml"),
                    BaselineIdea.&addCheckstyleExternalDependencies)
        }
    }

    private void addCheckstyleNode(Node node) {
        def checkstyleFile = "LOCAL_FILE:\$PROJECT_DIR\$/.baseline/checkstyle/checkstyle.xml"
        String checkstyleVersion = project.extensions.getByType(CheckstyleExtension.class).getToolVersion();
        node.append(new XmlParser().parseText("""
            <component name="CheckStyle-IDEA">
              <option name="configuration">
                <map>
                  <entry key="active-configuration" value="${checkstyleFile}:Baseline Checkstyle" />
                  <entry key="checkstyle-version" value="${checkstyleVersion}" />
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

        // language=xml
        node.append(new XmlParser().parseText('''
            <component name="VcsDirectoryMappings">
                <mapping directory="$PROJECT_DIR$" vcs="Git" />
            </component>
            '''.stripIndent()))
    }

    private static void addInspectionProjectProfile(node) {
        // language=xml
        node.append(new XmlParser().parseText("""
            <component name="InspectionProjectProfileManager">
                <profile version="1.0">
                    <option name="myName" value="Project Default" />
                    <inspection_tool class="MissingOverrideAnnotation" enabled="true" level="WARNING" enabled_by_default="true">
                        <option name="ignoreObjectMethods" value="false" />
                        <option name="ignoreAnonymousClassMethods" value="false" />
                    </inspection_tool>
                        
                    <inspection_tool class="PlaceholderCountMatchesArgumentCount" enabled="false" level="WARNING" enabled_by_default="false" />
                    
                    <inspection_tool class="ClassCanBeRecord" enabled="false" level="WEAK WARNING" enabled_by_default="false" />

                    <inspection_tool class="UnstableApiUsage" enabled="true" level="WARNING" enabled_by_default="true">
                        <option name="unstableApiAnnotations">
                            <set>
                                <option value="com.google.common.annotations.Beta" />
                                <option value="com.palantir.conjure.java.lib.internal.Incubating" />
                                <option value="io.reactivex.annotations.Beta" />
                                <option value="io.reactivex.annotations.Experimental" />
                                <option value="org.apache.http.annotation.Beta" />
                                <option value="org.gradle.api.Incubating" />
                                <option value="org.jetbrains.annotations.ApiStatus.Experimental" />
                                <option value="org.jetbrains.annotations.ApiStatus.Internal" />
                                <option value="org.jetbrains.annotations.ApiStatus.ScheduledForRemoval" />
                                <option value="rx.annotations.Beta" />
                                <option value="rx.annotations.Experimental" />
                            </set>
                        </option>
                    </inspection_tool>
                </profile>
                <option name="PROJECT_PROFILE" value="Project Default" />
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
        // language=xml
        node.append(new XmlParser().parseText("""
            <component name="CodeInsightWorkspaceSettings">
                <option name="optimizeImportsOnTheFly" value="true" />
              </component>
            """.stripIndent()))
    }

    private static void addGitHubIssueNavigation(Node node) {
        GitUtils.maybeGitHubUri().ifPresent {githubUri ->
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

    private static void addExcludedAutoImports(Node node) {
        // language=xml
        node.append(new XmlParser().parseText('''
            <component name="JavaProjectCodeInsightSettings">
              <excluded-names>
                <name>shadow</name><!-- from gradle-shadow-jar -->
                <name>org.junit.jupiter.params.shadow</name><!-- shaded deps from junit5 -->
                <name>org.gradle.internal.impldep</name>
                <name>autovalue.shaded</name>
                <name>org.inferred.freebuilder.shaded</name>
                <name>org.immutables.value.internal</name>
                <name>com.palantir.conjure.java.client.config.ImmutablesStyle</name>
                <name>com.palantir.sls.versions.ImmutablesStyle</name>
                <name>com.palantir.tokens.auth.ImmutablesStyle</name>
              </excluded-names>
            </component>
        '''.stripIndent()))
    }

    private static void configureSaveActionsForIntellijImport(Project project) {
        if (!IntellijSupport.isRunningInIntellij()) {
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
        runTypes.each {runType ->
            def configuration = GroovyXmlUtils.matchOrCreateChild(runManager, 'configuration',
                    [default: 'true', type: runType],
                    [factoryName: runType])
            def workingDirectory = GroovyXmlUtils.matchOrCreateChild(configuration, 'option',
                    [name: 'WORKING_DIRECTORY'])
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
        ideaModel.module.iml.whenMerged {module ->
            def projectRefs = module.dependencies.findAll {it instanceof ModuleDependency}
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
