package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineEclipse

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class BaselineEclipseTest {
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineEclipse
        project.evaluate()
    }

    @Test
    public void baselineEclipsePluginApplied() {
        assertTrue project.plugins.hasPlugin(BaselineEclipse.class)
    }

    @Test
    public void baselineEclipseCreatesEclipseTemplateTask() {
        assertNotNull project.tasks.eclipseTemplate
    }

    @Test
    public void eclipseTaskDependsOnEclipseTemplateTask() {
        assertTrue project.tasks.eclipse.dependsOn.contains(project.tasks.eclipseTemplate)
    }
}
