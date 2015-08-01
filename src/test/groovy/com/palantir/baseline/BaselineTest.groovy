package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineCheckstyle
import com.palantir.baseline.plugins.BaselineEclipse
import com.palantir.baseline.plugins.BaselineFindBugs
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
    public void baselineFindBugsProjectApplied() {
        assertTrue project.plugins.hasPlugin(BaselineFindBugs.class)
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
