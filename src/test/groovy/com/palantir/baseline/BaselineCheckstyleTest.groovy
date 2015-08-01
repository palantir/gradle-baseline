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
