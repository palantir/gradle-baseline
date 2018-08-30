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

import com.palantir.baseline.plugins.BaselineVersions
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaselineVersionsTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply BaselineVersions
    }

    def baselineVersionsApplied() {
        expect:
        project.plugins.hasPlugin(BaselineVersions.class)
        project.plugins.hasPlugin(DependencyRecommendationsPlugin.class)
    }

    def baselinesVersionsTaskExist() {
        expect:
        project.tasks.getByName("checkBomConflict")
        project.tasks.getByName("checkNoUnusedPin")
        project.tasks.getByName("checkVersionsProps")
    }
}
