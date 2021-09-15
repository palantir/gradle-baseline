/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins;

import com.palantir.baseline.extensions.BaselineJavaVersionExtension;
import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;

public final class BaselineJavaVersions implements Plugin<Project> {

    public static final String EXTENSION_NAME = "javaVersions";

    @Override
    public void apply(Project project) {
        if (!Objects.equals(project, project.getRootProject())) {
            throw new GradleException("BaselineJavaVersions may only be applied to the root project");
        }
        BaselineJavaVersionsExtension rootExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionsExtension.class, project);
        project.allprojects(proj -> {
            proj.getPluginManager().withPlugin("java", unused -> {
                proj.getPluginManager().apply(BaselineJavaVersion.class);
                BaselineJavaVersionExtension projectVersions =
                        proj.getExtensions().getByType(BaselineJavaVersionExtension.class);
                if (!projectVersions.target().isPresent()) {
                    projectVersions.target().set(proj.provider(() -> {
                        PublishingExtension publishing = proj.getExtensions().findByType(PublishingExtension.class);
                        boolean library = publishing == null
                                || publishing.getPublications().stream()
                                        .anyMatch(pub -> isLibraryPublication(proj, pub));
                        return library
                                ? rootExtension.libraryTarget().get()
                                : rootExtension.distributionTarget().get();
                    }));
                }
            });
        });
    }

    private static boolean isLibraryPublication(Project project, Publication publication) {
        if (publication instanceof MavenPublication) {
            MavenPublication mavenPublication = (MavenPublication) publication;
            return mavenPublication.getArtifacts().stream().anyMatch(artifact -> "jar".equals(artifact.getExtension()));
        }
        if (publication instanceof IvyPublication) {
            IvyPublication ivyPublication = (IvyPublication) publication;
            return ivyPublication.getArtifacts().stream().anyMatch(artifact -> "jar".equals(artifact.getExtension()));
        }
        // Default to true for unknown publication types to avoid setting higher jvm targets than necessary
        project.getLogger()
                .warn(
                        "Unknown publication '{}' of type '{}'. Assuming project {} is a library",
                        publication,
                        publication.getClass().getName(),
                        project.getName());
        return true;
    }
}
