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

    /**
     * We have a few different string representations of project artifactNames.
     */
    public static boolean isProjectArtifact(String artifactName) {
        return artifactName.startsWith("project :")
                || artifactName.startsWith("project (')")
                //a colon in the name (when the name doesn't start with "project" indicates it is a jar dependency
                // of the form group:id
                || !artifactName.contains(":");
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
        if (isProjectArtifact(result)) {
            //surround the project name with quotes and parents
            result = result.replace("project ", "project('") + "')";
        }
        return "implementation " + result;
    }
}
