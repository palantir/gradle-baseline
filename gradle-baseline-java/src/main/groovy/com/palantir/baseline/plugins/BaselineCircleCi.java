/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.configurationresolver.ConfigurationResolverPlugin;
import com.palantir.gradle.circlestyle.CircleStylePlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.profile.ProfileListener;
import org.gradle.profile.ProfileReportRenderer;

public final class BaselineCircleCi implements Plugin<Project> {
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private static final FileAttribute<Set<PosixFilePermission>> PERMS_ATTRIBUTE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(CircleStylePlugin.class);
        String circleArtifactsDir = System.getenv("CIRCLE_ARTIFACTS");
        if (circleArtifactsDir == null) {
            return;
        }

        try {
            Files.createDirectories(Paths.get(circleArtifactsDir), PERMS_ATTRIBUTE);
        } catch (IOException e) {
            throw new RuntimeException("failed to create CIRCLE_ARTIFACTS directory", e);
        }

        // the `./gradlew resolveConfigurations` task is used on CI to download all jars for convenient caching
        project.getRootProject().allprojects(proj ->
                proj.getPluginManager().apply(ConfigurationResolverPlugin.class));

        project.getRootProject().allprojects(proj ->
                proj.getTasks().withType(Test.class, test -> {
                    Path junitReportsDir = Paths.get(circleArtifactsDir, "junit");
                    for (String component : test.getPath().substring(1).split(":")) {
                        junitReportsDir = junitReportsDir.resolve(component);
                    }
                    test.getReports().getHtml().setEnabled(true);
                    test.getReports().getHtml().setDestination(junitReportsDir.toFile());
                }));

        if (project.getGradle().getStartParameter().isProfile()) {
            project.getGradle().addListener((ProfileListener) buildProfile -> {
                ProfileReportRenderer renderer = new ProfileReportRenderer();
                File file = Paths.get(circleArtifactsDir, "profile", "profile-"
                        + FILE_DATE_FORMAT.format(new Date(buildProfile.getBuildStarted())) + ".html").toFile();
                renderer.writeTo(buildProfile, file);
            });
        }
    }
}
