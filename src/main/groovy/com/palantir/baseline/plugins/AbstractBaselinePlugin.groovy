package com.palantir.baseline.plugins

import com.palantir.baseline.BaselineParameters
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Paths

/**
 * The super class of all Baseline plugins.
 */
abstract class AbstractBaselinePlugin implements Plugin<Project> {

    /** The {@link Project} that this plugin has been applied to; must be set in the {@link Project#apply} method. */
    protected Project project

    /** Returns the absolute path of the Baseline configuration, i.e., the directory '.baseline' in the root directory
     * of this project. */
    protected final String getConfigDir() {
        return Paths.get(project.rootDir.toString(), BaselineParameters.DEFAULT_CONFIG_DIR).toString()
    }
}
