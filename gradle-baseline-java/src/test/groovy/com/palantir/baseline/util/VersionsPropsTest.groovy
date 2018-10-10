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

package com.palantir.baseline.util

import spock.lang.Specification

class VersionsPropsTest extends Specification {
    def readVersionsProps() {
        def lines = [
                "  a:b  = c",
                "# linter:OFF",
                "d:eeee = 1",
                "# linter:ON",
                "b:c=2  # this is a comment",
        ]

        when:
        def parsedVersionsProps = VersionsProps.readVersionsProps(lines.stream())

        then:
        parsedVersionsProps.forces() == [
                VersionsProps.VersionForce.of("a:b", "c"),
                VersionsProps.VersionForce.of("b:c", "2"),
        ]

        parsedVersionsProps.lines() == lines.collect { it.trim() }
    }
}
