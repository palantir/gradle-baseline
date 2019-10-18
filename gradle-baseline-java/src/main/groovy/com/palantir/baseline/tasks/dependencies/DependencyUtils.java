package com.palantir.baseline.tasks.dependencies;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.yaml.snakeyaml.Yaml;

public final class DependencyUtils {

    private DependencyUtils() {
    }

    /**
     * Get artifact name as we store it in the dependency report.  This is also how artifacts are representede in
     * build.gradle files and the reports generated from the main gradle dependencies task
     */
    public static String getArtifactName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
        return isProjectArtifact(artifact)
                ? String.format("project :%s",
                ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectName())
                : String.format("%s:%s", id.getGroup(), id.getName());
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    public static boolean isProjectArtifact(ResolvedArtifact artifact) {
        return artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
    }

    public static DependencyReportTask.ReportContent getReportContent(File reportFile) {
        Yaml yaml = new Yaml();
        DependencyReportTask.ReportContent reportContent;
        try (InputStream input = Files.newInputStream(reportFile.toPath())) {
            reportContent = yaml.loadAs(input, DependencyReportTask.ReportContent.class);
        } catch (IOException e) {
            throw new RuntimeException("Error reading dependency report", e);
        }
        return reportContent;
    }

}
