package com.palantir.baseline.plugins;

import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Extracts Baseline configuration into the configuration directory.
 */
class BaselineConfig extends AbstractBaselinePlugin {

    public void apply(Project rootProject) {
        this.project = rootProject;

        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new IllegalArgumentException(
                    BaselineConfig.class.getCanonicalName() + " plugin can only be applied to the root project.");
        }

        Configuration configuration = rootProject.getConfigurations().create("baseline");

        // users can still override this default dependency, it just reduces boilerplate
        Optional<String> version = Optional.ofNullable(getClass().getPackage().getImplementationVersion());
        configuration.defaultDependencies(d -> d.add(project.getDependencies().create(String.format(
                "com.palantir.baseline:gradle-baseline-java-config%s@zip", version.map(v -> ":" + v).orElse("")))));

        // Create task for generating configuration.
        rootProject.getTasks().create("baselineUpdateConfig", task -> {
            task.setGroup("Baseline");
            task.setDescription("Installs or updates Baseline configuration files in .baseline/");
            task.doLast(t -> {

                if (configuration.getFiles().size() != 1) {
                    throw new IllegalArgumentException("Expected to find exactly one config dependency in the "
                            + "'baseline' configuration, found: " + configuration.getFiles());
                }

                rootProject.copy(copySpec -> {
                    copySpec.from(project.zipTree(configuration.getSingleFile()));
                    copySpec.into(getConfigDir());
                });
            });
        });
    }
}
