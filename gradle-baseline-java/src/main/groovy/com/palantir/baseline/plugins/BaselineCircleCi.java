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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.palantir.gradle.junit.JunitReportsExtension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

public final class BaselineCircleCi implements Plugin<Project> {
    private static final FileAttribute<Set<PosixFilePermission>> PERMS_ATTRIBUTE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));

    @Override
    public void apply(Project project) {
        configurePluginsForReports(project);
        configurePluginsForArtifacts(project);

        Preconditions.checkState(
                !project.getName().equals("project"),
                "Please specify rootProject.name in your settings.gradle, otherwise CircleCI's"
                        + "checkout dir ('project') will be used instead.");
    }

    private void configurePluginsForArtifacts(Project project) {
        String circleArtifactsDir = System.getenv("CIRCLE_ARTIFACTS");
        if (circleArtifactsDir == null) {
            project.getLogger().info("$CIRCLE_ARTIFACTS variable is not set, not configuring junit/profiling reports");
            return;
        }

        try {
            Files.createDirectories(Paths.get(circleArtifactsDir), PERMS_ATTRIBUTE);
        } catch (IOException e) {
            throw new RuntimeException("failed to create CIRCLE_ARTIFACTS directory", e);
        }

        project.getRootProject()
                .allprojects(proj -> proj.getTasks().withType(Test.class).configureEach(test -> {
                    test.getReports().getHtml().getRequired().set(true);
                    test.getReports().getHtml().getOutputLocation().set(junitPath(circleArtifactsDir, test.getPath()));
                }));
    }

    private void configurePluginsForReports(Project project) {
        String circleReportsDir = System.getenv("CIRCLE_TEST_REPORTS");
        if (circleReportsDir == null) {
            return;
        }

        try {
            Files.createDirectories(Paths.get(circleReportsDir), PERMS_ATTRIBUTE);
        } catch (IOException e) {
            throw new RuntimeException("failed to create CIRCLE_TEST_REPORTS directory", e);
        }

        project.getRootProject()
                .allprojects(proj -> proj.getTasks().withType(Test.class).configureEach(test -> {
                    test.getReports().getJunitXml().getRequired().set(true);
                    test.getReports()
                            .getJunitXml()
                            .getOutputLocation()
                            .set(junitPath(circleReportsDir, test.getPath()));
                }));

        project.getPluginManager().withPlugin("com.palantir.junit-reports", unused -> {
            project.getExtensions().configure(JunitReportsExtension.class, junitReports -> junitReports
                    .getReportsDirectory()
                    .set(new File(circleReportsDir)));
        });
    }

    private static File junitPath(String basePath, String testPath) {
        Path junitReportsDir = Paths.get(basePath, "junit");
        for (String component : Splitter.on(":").split(testPath.substring(1))) {
            junitReportsDir = junitReportsDir.resolve(component);
        }
        return junitReportsDir.toFile();
    }
}
