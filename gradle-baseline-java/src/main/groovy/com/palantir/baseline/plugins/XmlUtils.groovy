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

package com.palantir.baseline.plugins

import com.google.common.collect.ImmutableMap
import com.google.common.io.Files
import java.nio.charset.Charset
import java.util.function.Consumer
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

final class XmlUtils {
    static Node matchOrCreateChild(Node base, String name, Map attributes = [:], Map defaults = [:],
                                   @DelegatesTo(Node) Closure ifCreated = {}) {
        def child = base[name].find { it.attributes().entrySet().containsAll(attributes.entrySet()) }
        if (child) {
            return child
        }

        def created = base.appendNode(name, attributes + defaults)
        ifCreated(created)
        return created
    }

    static void createOrUpdateXmlFile(File configurationFile, Consumer<Node> configure) {
        Node rootNode;
        if (configurationFile.isFile()) {
            try {
                rootNode = new XmlParser().parse(configurationFile);
            } catch (IOException | SAXException | ParserConfigurationException e) {
                throw new RuntimeException("Couldn't parse existing configuration file: " + configurationFile, e);
            }
        } else {
            rootNode = new Node(null, "project", ImmutableMap.of("version", "4"));
        }

        configure(rootNode);

        try (BufferedWriter writer = Files.newWriter(configurationFile, Charset.defaultCharset());
             PrintWriter printWriter = new PrintWriter(writer)) {
            XmlNodePrinter nodePrinter = new XmlNodePrinter(printWriter);
            nodePrinter.setPreserveWhitespace(true);
            nodePrinter.print(rootNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write back to configuration file: " + configurationFile, e);
        }
    }
}
