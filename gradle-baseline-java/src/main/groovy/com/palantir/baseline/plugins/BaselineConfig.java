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

package com.palantir.baseline.plugins;

import com.palantir.baseline.plugins.BaselineFormat.FormatterState;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Extracts Baseline configuration into the configuration directory. */
class BaselineConfig extends AbstractBaselinePlugin {

    public void apply(Project rootProject) {
        this.project = rootProject;

        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new IllegalArgumentException(
                    BaselineConfig.class.getCanonicalName() + " plugin can only be applied to the root project.");
        }

        Configuration configuration = rootProject.getConfigurations().create("baseline");

        // users can still override this default dependency, it just reduces boilerplate
        Optional<String> version = Optional.ofNullable(getClass().getPackage().getImplementationVersion());
        configuration.defaultDependencies(d -> d.add(rootProject
                .getDependencies()
                .create(String.format(
                        "com.palantir.baseline:gradle-baseline-java-config%s@zip",
                        version.map(v -> ":" + v).orElse("")))));

        // Create task for generating configuration.
        rootProject.getTasks().register("baselineUpdateConfig", task -> {
            task.setGroup("Baseline");
            task.setDescription("Installs or updates Baseline configuration files in .baseline/");
            task.getInputs().files(configuration);
            task.getOutputs().dir(getConfigDir());
            task.getOutputs().dir(rootProject.getRootDir().toPath().resolve("project"));
            task.doLast(new BaselineUpdateConfigAction(configuration, rootProject));
        });
    }

    private class BaselineUpdateConfigAction implements Action<Task> {
        private final Configuration configuration;
        private final Project rootProject;

        BaselineUpdateConfigAction(Configuration configuration, Project rootProject) {
            this.configuration = configuration;
            this.rootProject = rootProject;
        }

        @Override
        public void execute(Task task) {
            if (configuration.getFiles().size() != 1) {
                throw new IllegalArgumentException("Expected to find exactly one config dependency in the "
                        + "'baseline' configuration, found: "
                        + configuration.getFiles());
            }

            Path configDir = Paths.get(BaselineConfig.this.getConfigDir());
            rootProject.copy(copySpec -> {
                copySpec.from(rootProject.zipTree(configuration.getSingleFile()));
                copySpec.into(configDir);
                copySpec.exclude("**/scalastyle_config.xml");
                copySpec.setIncludeEmptyDirs(false);

                if (!BaselineFormat.eclipseFormattingEnabled(task.getProject())) {
                    copySpec.exclude("**/spotless/eclipse.xml");
                }
            });

            // Disable some checkstyle rules that clash with PJF
            if (BaselineFormat.palantirJavaFormatterState(rootProject) != FormatterState.OFF
                    || project.getPluginManager().hasPlugin("com.palantir.java-format-provider")) {
                Path checkstyleXml = configDir.resolve("checkstyle/checkstyle.xml");

                try {
                    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = builderFactory.newDocumentBuilder();

                    InputSource inputSource = new InputSource(new FileReader(checkstyleXml.toFile()));
                    inputSource.setEncoding("UTF-8");
                    Document document = builder.parse(inputSource);

                    XPathFactory xPathFactory = XPathFactory.newInstance();
                    XPath xPath = xPathFactory.newXPath();

                    removeNode(document, xPath, "//module[@name='Indentation']");
                    removeNode(document, xPath, "//module[@name='ParenPad']");
                    removeNode(document, xPath, "//module[@name='LeftCurly']");
                    removeNode(document, xPath, "//module[@name='WhitespaceAround']");

                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

                    DOMSource source = new DOMSource(document);
                    StreamResult result = new StreamResult(new FileWriter(checkstyleXml.toFile()));
                    transformer.transform(source, result);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to patch " + checkstyleXml, e);
                }
            }

            if (rootProject.getAllprojects().stream()
                    .anyMatch(p -> p.getPluginManager().hasPlugin("scala")
                            && p.getPluginManager().hasPlugin("com.palantir.baseline-scalastyle"))) {
                // Matches intellij scala plugin settings per
                // https://github.com/JetBrains/intellij-scala/blob/baaa7c1dabe5222c4bca7c4dd8d80890ad2a8c6b/scala/scala-impl/src/org/jetbrains/plugins/scala/codeInspection/scalastyle/ScalastyleCodeInspection.scala#L19
                rootProject.copy(copySpec -> {
                    copySpec.from(
                            rootProject.zipTree(configuration.getSingleFile()).filter(file -> file.getName()
                                    .equals("scalastyle_config.xml")));
                    copySpec.into(rootProject.getRootDir().toPath().resolve("project"));
                    copySpec.setIncludeEmptyDirs(false);
                });
            }
        }

        private void removeNode(Document document, XPath xPath, String expression) throws XPathExpressionException {
            xPath.reset();
            Node node = (Node) xPath.compile(expression).evaluate(document, XPathConstants.NODE);
            node.getParentNode().removeChild(node);
        }
    }
}
