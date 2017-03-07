/*
 * Copyright 2015 Palantir Technologies, Inc.
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

import com.palantir.baseline.plugins.BaselineEclipse
import spock.lang.Specification

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class BaselineEclipseTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineEclipse
        project.evaluate()
    }

    def baselineEclipsePluginApplied() {
        expect:
        assertTrue project.plugins.hasPlugin(BaselineEclipse.class)
    }

    def baselineEclipseCreatesEclipseTemplateTask() {
        expect:
        project.tasks.eclipseTemplate
    }

    def eclipseTaskDependsOnEclipseTemplateTask() {
        expect:
        project.tasks.eclipse.dependsOn.contains(project.tasks.eclipseTemplate)
    }
}
