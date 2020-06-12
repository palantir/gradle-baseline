/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.io.Files;
import groovy.util.Node;
import groovy.util.XmlNodePrinter;
import groovy.util.XmlParser;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.inject.Provider;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

final class XmlUtils {
    private XmlUtils() {}

    static void createOrUpdateXmlFile(File configurationFile, Consumer<Node> configure) {
        createOrUpdateXmlFile(
                configurationFile, configure, () -> new Node(null, "project", ImmutableMap.of("version", "4")));
    }

    static void createOrUpdateXmlFile(
            File configurationFile, Consumer<Node> configure, Provider<Node> defaultRootNode) {
        Node rootNode;
        if (configurationFile.isFile()) {
            try {
                rootNode = new XmlParser().parse(configurationFile);
            } catch (IOException | SAXException | ParserConfigurationException e) {
                throw new RuntimeException("Couldn't parse existing configuration file: " + configurationFile, e);
            }
        } else {
            rootNode = defaultRootNode.get();
        }

        configure.accept(rootNode);

        configurationFile.getParentFile().mkdirs();

        try (BufferedWriter writer = Files.newWriter(configurationFile, StandardCharsets.UTF_8);
                PrintWriter printWriter = new PrintWriter(writer)) {
            XmlNodePrinter nodePrinter = new XmlNodePrinter(printWriter);
            nodePrinter.setPreserveWhitespace(true);
            nodePrinter.print(rootNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write back to configuration file: " + configurationFile, e);
        }
    }
}
