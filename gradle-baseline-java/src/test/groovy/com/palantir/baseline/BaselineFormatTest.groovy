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

package com.palantir.baseline

import static org.junit.Assert.assertTrue

import com.palantir.baseline.plugins.BaselineFormat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineFormatTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.buildscript {
            repositories {
                mavenCentral()
            }
        }
        project.plugins.apply 'java'
        project.plugins.apply BaselineFormat
        project.evaluate()
    }

    def spotlessPluginApplied() {
        expect:
        assertTrue project.plugins.hasPlugin("com.diffplug.spotless")
    }

    def baselineFormatCreatesFormatTask() {
        expect:
        project.tasks.format
    }

    def spotlessPluginEagerCreationIssue() {
        when:
        project.buildscript {
            repositories {
                mavenCentral()
            }

            project.tasks.register("foo") {
                throw new SpotlessEagerConfigException("See https://github.com/diffplug/spotless/issues/444")
            }
        }

        then:
        notThrown(SpotlessEagerConfigException)
    }

    static final class SpotlessEagerConfigException extends RuntimeException {

    }
}
