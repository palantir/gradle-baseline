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

final class GroovyXmlUtils {
    static Node matchOrCreateChild(Node base, String name, Map attributes = [:], Map defaults = [:],
                                   @DelegatesTo(value = Node, strategy = Closure.DELEGATE_FIRST)
                                           Closure ifCreated = {}) {
        def child = base[name].find { it.attributes().entrySet().containsAll(attributes.entrySet()) }
        if (child) {
            return child
        }

        def created = base.appendNode(name, attributes + defaults)
        ifCreated.delegate = created
        ifCreated(created)
        return created
    }
}
