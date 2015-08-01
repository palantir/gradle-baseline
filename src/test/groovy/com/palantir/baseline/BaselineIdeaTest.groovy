package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineIdea
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.*

class BaselineIdeaTest {
    private Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply 'idea'
        project.plugins.apply BaselineIdea
        project.evaluate()
    }

    @Test
    public void baselineIdeaPluginApplied() {
        assertTrue project.plugins.hasPlugin(BaselineIdea.class)
    }
}
