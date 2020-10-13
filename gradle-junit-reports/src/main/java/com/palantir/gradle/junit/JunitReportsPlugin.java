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

package com.palantir.gradle.junit;

import com.google.common.base.Splitter;
import java.io.File;
import java.nio.file.Path;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.build.event.BuildEventsListenerRegistry;

public final class JunitReportsPlugin implements Plugin<Project> {

    public static final String EXT_JUNIT_REPORTS = "junitReports";

    private final BuildEventsListenerRegistry listenerRegistry;

    @Inject
    JunitReportsPlugin(BuildEventsListenerRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    @SuppressWarnings("Slf4jLogsafeArgs")
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            project.getLogger()
                    .warn(
                            "com.palantir.junit-reports should be applied to the root project only, not '{}'",
                            project.getName());
        }

        JunitReportsExtension reportsExtension =
                project.getExtensions().create(EXT_JUNIT_REPORTS, JunitReportsExtension.class, project);

        Provider<File> reportTargetFile = reportTargetFile(reportsExtension.getReportsDirectory());
        Provider<BuildJunitReportService> listenerProvider = project.getGradle()
                .getSharedServices()
                .registerIfAbsent(
                        "build-failure-listener",
                        BuildJunitReportService.class,
                        spec -> spec.parameters(params -> params.setReportFile(reportTargetFile.get())));
        listenerRegistry.onTaskCompletion(listenerProvider);

        TaskTimer timer = new StyleTaskTimer();
        project.getRootProject().getGradle().addListener(timer);

        project.getRootProject().allprojects(proj -> {
            proj.getTasks().withType(Test.class, test -> {
                test.getReports().getJunitXml().setEnabled(true);
                test.getReports()
                        .getJunitXml()
                        .setDestination(junitPath(reportsExtension.getReportsDirectory(), test.getPath()));
            });
            proj.getTasks()
                    .withType(
                            Checkstyle.class,
                            checkstyle -> JunitReportsFinalizer.registerFinalizer(
                                    checkstyle,
                                    timer,
                                    XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()),
                                    reportsExtension.getReportsDirectory().map(dir -> dir.dir("checkstyle"))));
            proj.getTasks()
                    .withType(
                            JavaCompile.class,
                            javac -> JunitReportsFinalizer.registerFinalizer(
                                    javac,
                                    timer,
                                    JavacFailuresSupplier.create(javac),
                                    reportsExtension.getReportsDirectory().map(dir -> dir.dir("javac"))));
        });
    }

    private static Provider<File> junitPath(DirectoryProperty basePath, String testPath) {
        return basePath.map(dir -> dir.dir("junit"))
                .map(dir ->
                        dir.file(String.join(File.separator, Splitter.on(':').splitToList(testPath.substring(1)))))
                .map(RegularFile::getAsFile);
    }

    private static Provider<File> reportTargetFile(Property<Directory> reportsDir) {
        return reportsDir.map(dir -> {
            int attemptNumber = 1;
            Path targetFile = dir.getAsFile().toPath().resolve("gradle").resolve("build.xml");
            while (targetFile.toFile().exists()) {
                targetFile = dir.getAsFile().toPath().resolve("gradle").resolve("build" + ++attemptNumber + ".xml");
            }
            return dir.file(targetFile.toAbsolutePath().toString()).getAsFile();
        });
    }
}
