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
import org.w3c.dom.NodeList;
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

        private final String inclusiveCodeCheckOn = "inclusive-language";

        private final String checkstylePathString = "checkstyle/checkstyle.xml";

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

            Path checkstylePath = configDir.resolve(checkstylePathString);

            // Disable some checkstyle rules that clash with PJF
            if (BaselineFormat.palantirJavaFormatterState(rootProject) != FormatterState.OFF
                    || project.getPluginManager().hasPlugin("com.palantir.java-format-provider")) {

                try {
                    Document document = getDocument(checkstylePath);
                    XPath xPath = XPathFactory.newInstance().newXPath();

                    removeNode(document, xPath, "//module[@name='Indentation']");
                    removeNode(document, xPath, "//module[@name='ParenPad']");
                    removeNode(document, xPath, "//module[@name='LeftCurly']");
                    removeNode(document, xPath, "//module[@name='WhitespaceAround']");

                    writeEditedCheckstyle(document, checkstylePath);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to patch " + checkstylePath, e);
                }
            }

            if (!project.hasProperty(inclusiveCodeCheckOn)) {
                try {
                    Document document = getDocument(configDir.resolve(checkstylePathString));
                    XPath xPath = XPathFactory.newInstance().newXPath();

                    removeAllMatchingNodes(document, xPath, "//module/property[contains(@value, 'inclusive')]");

                    writeEditedCheckstyle(document, checkstylePath);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to patch " + checkstylePath, e);
                }
            }
        }

        private Document getDocument(Path checkstylePath) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();

                InputSource inputSource = new InputSource(new FileReader(checkstylePath.toFile()));
                inputSource.setEncoding("UTF-8");
                return builder.parse(inputSource);
            } catch (Exception e) {
                throw new RuntimeException("Unable to patch " + checkstylePath, e);
            }
        }

        private void writeEditedCheckstyle(Document document, Path checkstylePath) {
            try {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_PUBLIC, document.getDoctype().getPublicId());
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_SYSTEM, document.getDoctype().getSystemId());

                DOMSource source = new DOMSource(document);
                StreamResult result = new StreamResult(new FileWriter(checkstylePath.toFile()));
                transformer.transform(source, result);
            } catch (Exception e) {
                throw new RuntimeException("Unable to patch " + checkstylePath, e);
            }
        }

        private void removeAllMatchingNodes(Document document, XPath xPath, String expression)
                throws XPathExpressionException {
            try {
                xPath.reset();
                NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    node.getParentNode().removeChild(node);
                }
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        private void removeNode(Document document, XPath xPath, String expression) throws XPathExpressionException {
            xPath.reset();
            Node node = (Node) xPath.compile(expression).evaluate(document, XPathConstants.NODE);
            node.getParentNode().removeChild(node);
        }
    }
}
