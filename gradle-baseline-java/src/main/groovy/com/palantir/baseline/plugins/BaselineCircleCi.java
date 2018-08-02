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

import com.google.common.base.Splitter;
import com.palantir.configurationresolver.ConfigurationResolverPlugin;
import com.palantir.gradle.circlestyle.CheckstyleReportHandler;
import com.palantir.gradle.circlestyle.CircleBuildFailureListener;
import com.palantir.gradle.circlestyle.CircleBuildFinishedAction;
import com.palantir.gradle.circlestyle.CircleStyleFinalizer;
import com.palantir.gradle.circlestyle.JavacFailuresSupplier;
import com.palantir.gradle.circlestyle.StyleTaskTimer;
import com.palantir.gradle.circlestyle.TaskTimer;
import com.palantir.gradle.circlestyle.XmlReportFailuresSupplier;
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
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.profile.ProfileListener;
import org.gradle.profile.ProfileReportRenderer;

public final class BaselineCircleCi implements Plugin<Project> {
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private static final FileAttribute<Set<PosixFilePermission>> PERMS_ATTRIBUTE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            project.getLogger().warn(
                    "com.palantir.baseline-circleci should be applied to the root project only, not '{}'",
                    project.getName());
        }

        // the `./gradlew resolveConfigurations` task is used on CI to download all jars for convenient caching
        project.getRootProject().allprojects(p -> p.getPluginManager().apply(ConfigurationResolverPlugin.class));
        configurePluginsForReports(project);
        configurePluginsForArtifacts(project);
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

        project.getRootProject().allprojects(proj ->
                proj.getTasks().withType(Test.class, test -> {
                    test.getReports().getHtml().setEnabled(true);
                    test.getReports().getHtml().setDestination(junitPath(circleArtifactsDir, test.getPath()));
                }));

        if (project.getGradle().getStartParameter().isProfile()) {
            project.getGradle().addListener((ProfileListener) buildProfile -> {
                ProfileReportRenderer renderer = new ProfileReportRenderer();
                File file = Paths.get(circleArtifactsDir, "profile", "profile-"
                        + fileDateFormat.format(new Date(buildProfile.getBuildStarted())) + ".html").toFile();
                renderer.writeTo(buildProfile, file);
            });
        }
    }

    private void configurePluginsForReports(Project project) {
        String circleReportsDir = System.getenv("CIRCLE_TEST_REPORTS");
        if (circleReportsDir == null) {
            project.getLogger().info("CIRCLE_TEST_REPORTS variable is not set,"
                    + " not configuring junit/checkstyele/java compilation results");
            return;
        }

        try {
            Files.createDirectories(Paths.get(circleReportsDir), PERMS_ATTRIBUTE);
        } catch (IOException e) {
            throw new RuntimeException("failed to create CIRCLE_TEST_REPORTS directory", e);
        }

        configureBuildFailureFinalizer(project.getRootProject(), circleReportsDir);

        TaskTimer timer = new StyleTaskTimer();
        project.getRootProject().getGradle().addListener(timer);

        project.getRootProject().allprojects(proj -> {
            proj.getTasks().withType(Test.class, test -> {
                test.getReports().getJunitXml().setEnabled(true);
                test.getReports().getJunitXml().setDestination(junitPath(circleReportsDir, test.getPath()));
            });
            proj.getTasks().withType(Checkstyle.class, checkstyle ->
                    CircleStyleFinalizer.registerFinalizer(
                            checkstyle,
                            timer,
                            XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()),
                            Paths.get(circleReportsDir, "checkstyle")));
            proj.getTasks().withType(JavaCompile.class, javac ->
                    CircleStyleFinalizer.registerFinalizer(
                            javac,
                            timer,
                            JavacFailuresSupplier.create(javac),
                            Paths.get(circleReportsDir, "javac")));
        });
    }

    private static File junitPath(String basePath, String testPath) {
        Path junitReportsDir = Paths.get(basePath, "junit");
        for (String component : Splitter.on(":").split(testPath.substring(1))) {
            junitReportsDir = junitReportsDir.resolve(component);
        }
        return junitReportsDir.toFile();
    }

    private static void configureBuildFailureFinalizer(Project rootProject, String circleReportsDir) {
        int attemptNumber = 1;
        Path targetFile = Paths.get(circleReportsDir, "gradle", "build.xml");
        while (targetFile.toFile().exists()) {
            targetFile = Paths.get(circleReportsDir, "gradle", "build" + (++attemptNumber) + ".xml");
        }
        Integer container;
        try {
            container = Integer.parseInt(System.getenv("CIRCLE_NODE_INDEX"));
        } catch (NumberFormatException e) {
            container = null;
        }
        CircleBuildFailureListener listener = new CircleBuildFailureListener();
        CircleBuildFinishedAction action = new CircleBuildFinishedAction(container, targetFile, listener);
        rootProject.getGradle().addListener(listener);
        rootProject.getGradle().buildFinished(action);
    }
}
