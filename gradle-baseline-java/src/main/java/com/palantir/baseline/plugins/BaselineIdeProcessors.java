/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import groovy.xml.QName;
import java.io.File;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineIdeProcessors implements Plugin<Project> {
    private static final Logger log = LoggerFactory.getLogger(BaselineIdeProcessors.class);

    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(IdeaPlugin.class)) {
            configureIdea(project);
        }
        // TODO(tpetracca): eclipse support
    }

    private static void configureIdea(Project project) {
        IdeaModel idea = project.getExtensions().getByType(IdeaModel.class);

        // Ensure we're not importing from intellij before configuring these, otherwise we will conflict with Intellij's
        // own way of handling annotation processor output directories.
        if (!Boolean.getBoolean("idea.active")) {
            addGeneratedOutputToIdeaSourceFolder(project, idea, "generated_src", false);
            addGeneratedOutputToIdeaSourceFolder(project, idea, "generated_testSrc", true);
        }
    }

    private static void addGeneratedOutputToIdeaSourceFolder(
            Project project, IdeaModel idea, String outputDirName, boolean isTest) {
        File outputDir = project.file(outputDirName);
        String relativeOutputDirPath = project.relativePath(outputDirName);

        // add generated directory as source directory
        idea.getModule().getGeneratedSourceDirs().add(outputDir);
        if (!isTest) {
            idea.getModule().getSourceDirs().add(outputDir);
        } else {
            idea.getModule().getTestSourceDirs().add(outputDir);
        }

        // if generated source directory doesn't already exist, Gradle IDEA plugin will not add it as a source folder,
        // so manually add as generated source folder to the .iml
        idea.getModule().getIml().withXml(xmlProvider -> {
            String dirUrl = "file://$MODULE_DIR$/" + relativeOutputDirPath;
            Node rootNode = xmlProvider.asNode();
            Node component = children(rootNode, "component")
                    .filter(node -> node.attributes().get("name").equals("NewModuleRootManager"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("iml did not contain expected xml node: component"));
            Node content = children(component, "content")
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("iml did not contain expected xml node: content"));
            boolean hasSourceDirectoryAlready = children(content, "sourceFolder")
                    .anyMatch(node -> node.attributes().get("url").equals(dirUrl));
            if (!hasSourceDirectoryAlready) {
                content.appendNode(
                        "sourceFolder",
                        ImmutableMap.of(
                                "url", dirUrl,
                                "isTestSource", isTest,
                                "generated", true));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Stream<Node> children(Node parent, String childQname) {
        return parent.getAt(new QName(childQname)).stream();
    }
}
