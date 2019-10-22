/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.tasks.dependencies;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.Directory;
import org.yaml.snakeyaml.Yaml;

public final class DependencyUtils {

    private DependencyUtils() {
    }

    /**
     * Get dependency string for the given artifact.  The format differs between external dependencies (e.g. jars) and
     * projects in the same source root.  The format matches how dependencies are represented in reports generated from
     * the core gradle dependencies task and in build.gradle files, except the version is omitted.
     */
    public static String getDependencyName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
        return isProjectDependency(artifact)
                ? String.format("project :%s",
                ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectName())
                : getJarDependencyName(artifact);
    }

    /**
     * Generate dependency id in the standard way except for version - group:name[:classifier].
     */
    private static String getJarDependencyName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
        String classifier = artifact.getClassifier();

        String result = id.getGroup() + ":" + id.getName();
        if (classifier != null && !classifier.isEmpty()) {
            result = result + ":" + classifier;
        }
        return result;
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    public static boolean isProjectDependency(ResolvedArtifact artifact) {
        return artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
    }

    /**
     * We have a few different string representations of project dependency names.
     */
    public static boolean isProjectDependency(String artifactName) {
        return artifactName.startsWith("project :")
                || artifactName.startsWith("project (')")
                // a colon in the name (when the name doesn't start with "project" indicates it is a jar dependency
                // e.g. group:id
                || !artifactName.contains(":");
    }

    static void writeDepReport(File file, DependencyReportTask.ReportContent content) {
        Yaml yaml = new Yaml();
        try {
            String contents = yaml.dumpAsMap(content);
            Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Error writing dependency report", e);
        }
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

    /**
     * Turn an artifact name into a suggestion for how to add it to gradle dependencies.
     * TODO(esword): Add some way to determine if test or api or both
     */
    public static String getSuggestionString(String artifact) {
        String result = artifact;
        if (isProjectDependency(result)) {
            //surround the project name with quotes and parents
            result = result.replace("project ", "project('") + "')";
        }
        return "implementation " + result;
    }

    /**
     * The report directory will normally contain the main report and a summary report.  The main report's
     * name can vary, so find it by looking for whatever is not the summary.
     */
    static Optional<File> findDetailedDotReport(Directory dotFileDir) {
        return dotFileDir.getAsFileTree().getFiles().stream()
                .filter(f -> !f.isDirectory() && !f.getName().equals("summary.dot"))
                .findFirst();
    }
}
