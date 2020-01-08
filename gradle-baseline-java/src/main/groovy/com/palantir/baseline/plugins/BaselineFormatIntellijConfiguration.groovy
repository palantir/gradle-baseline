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

import static GroovyXmlUtils.matchOrCreateChild

final class BaselineFormatIntellijConfiguration {
    private BaselineFormatIntellijConfiguration() {}

    /**
     * Configures some defaults on the save-actions plugin, but only if it hasn't been configured before.
     */
    static void configureSaveActions(Node rootNode) {
        matchOrCreateChild(rootNode, 'component', [name: 'SaveActionSettings'], [:]) {
            // Configure defaults if this plugin is configured for the first time only
            appendNode('option', [name: 'actions']).appendNode('set').with {
                appendNode('option', [value: 'activate'])
                appendNode('option', [value: 'noActionIfCompileErrors'])
                appendNode('option', [value: 'organizeImports'])
                appendNode('option', [value: 'reformat'])
            }
            appendNode('option', [name: 'configurationPath', value: ''])
            appendNode('inclusions').appendNode('set').with {
                appendNode('option', [value: 'src/.*\\.java'])
            }
        }
    }

    static void configureExternalDependencies(Node rootNode) {
        def externalDependencies = matchOrCreateChild(rootNode, 'component', [name: 'ExternalDependencies'])
        matchOrCreateChild(externalDependencies, 'plugin', [id: 'save-actions'])
    }
}
