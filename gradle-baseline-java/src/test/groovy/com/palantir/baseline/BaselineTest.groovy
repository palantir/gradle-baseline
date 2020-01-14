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

import com.palantir.baseline.plugins.Baseline
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineTest extends Specification {

    private Project project
    private Project subProject

    def setup() {
        project = ProjectBuilder.builder().withName('rootProject').build()
        subProject = ProjectBuilder.builder().withParent(project).withName('subProject').build()
        project.plugins.apply Baseline
    }

    def appliesToRootProjectAndSubprojects() {
        expect:
        assert project.pluginManager.hasPlugin('com.palantir.baseline-circleci')
        assert project.pluginManager.hasPlugin('com.palantir.baseline-config')
        // eclipse plugin not applied to root project because it is not a java project
        assert !project.pluginManager.hasPlugin('eclipse')
        hasAllPlugins(project)
        hasAllPlugins(subProject)
    }

    void hasAllPlugins(Project p) {
        assert p.pluginManager.hasPlugin('com.palantir.baseline-checkstyle')
        assert p.pluginManager.hasPlugin('com.palantir.baseline-eclipse')
        assert p.pluginManager.hasPlugin('com.palantir.baseline-error-prone')
        assert p.pluginManager.hasPlugin('com.palantir.baseline-idea')
        assert p.pluginManager.hasPlugin('com.palantir.baseline-release-compatibility')
    }

}
