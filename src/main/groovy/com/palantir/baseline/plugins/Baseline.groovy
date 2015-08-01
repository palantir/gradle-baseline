package com.palantir.baseline.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Plugin that configures a project with all Baseline settings.
 */
class Baseline implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply BaselineCheckstyle
        project.plugins.apply BaselineFindBugs
        project.plugins.apply BaselineEclipse
        project.plugins.apply BaselineIdea
    }
}
