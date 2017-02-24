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

import com.palantir.baseline.plugins.BaselineCheckstyle
import com.palantir.baseline.plugins.BaselineEclipse

import com.palantir.baseline.plugins.Baseline

import static org.junit.Assert.assertTrue

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class BaselineTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none()
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply Baseline
        project.evaluate()
    }

    @Test
    public void baselineProjectPluginApplied() {
        assertTrue project.plugins.hasPlugin(Baseline.class)
    }

    @Test
    public void baselineCheckstyleProjectApplied() {
        assertTrue project.plugins.hasPlugin(BaselineCheckstyle.class)
    }

    @Test
    public void baselineEclipseProjectApplied() {
        assertTrue project.plugins.hasPlugin(BaselineEclipse.class)
    }
}
