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
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.isIn
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class BaselineCheckstyleTest {
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineCheckstyle
    }

    @Test
    public void baselineCheckstylePluginApplied() {
        assertTrue project.plugins.hasPlugin(BaselineCheckstyle.class)
    }

    @Test
    public void checkstylePluginApplied() {
        assertTrue project.plugins.hasPlugin(CheckstylePlugin.class)
    }

    @Test
    public void appliesEclipseNatures() {
        project.plugins.apply 'eclipse'
        project.plugins.apply BaselineEclipse

        project.plugins.findPlugin(BaselineCheckstyle).configureCheckstyleForEclipse()
        assertThat("net.sf.eclipsecs.core.CheckstyleNature", isIn(project.eclipse.project.natures))
    }
}
