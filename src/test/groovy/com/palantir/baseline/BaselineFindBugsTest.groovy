package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineFindBugs

import org.gradle.api.Project
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue

class BaselineFindBugsTest {
    private Project project

    @Before
    void setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'java'
        project.plugins.apply BaselineFindBugs
    }

    @Test
    void testPluginsAreApplied() {
        assertTrue project.plugins.hasPlugin(BaselineFindBugs.class)
        assertTrue project.plugins.hasPlugin(FindBugsPlugin.class)
    }
}
