/*
 * Copyright 2017 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.circlestyle;

import static com.palantir.gradle.circlestyle.CircleStyleFinalizer.registerFinalizer;

import java.io.File;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.FindBugs;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

public class CircleStylePlugin implements Plugin<Project> {

    @Override
    public void apply(Project rootProject) {
        final String circleReportsDir = System.getenv("CIRCLE_TEST_REPORTS");
        if (circleReportsDir == null) {
            return;
        }

        configureBuildFailureFinalizer(rootProject, circleReportsDir);

        final StyleTaskTimer timer = new StyleTaskTimer();
        rootProject.getGradle().addListener(timer);

        rootProject.allprojects(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                project.getTasks().withType(Test.class, new Action<Test>() {
                    @Override
                    public void execute(Test test) {
                        File junitReportsDir = new File(circleReportsDir, "junit");
                        for (String component : test.getPath().substring(1).split(":")) {
                            junitReportsDir = new File(junitReportsDir, component);
                        }
                        setDestination(test.getReports().getJunitXml(), junitReportsDir);
                    }
                });
                project.getTasks().withType(Checkstyle.class, new Action<Checkstyle>() {
                    @Override
                    public void execute(Checkstyle checkstyle) {
                        registerFinalizer(
                                checkstyle,
                                timer,
                                XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()),
                                new File(circleReportsDir, "checkstyle"));
                    }
                });
                project.getTasks().withType(FindBugs.class, new Action<FindBugs>() {
                    @Override
                    public void execute(FindBugs findbugs) {
                        registerFinalizer(
                                findbugs,
                                timer,
                                XmlReportFailuresSupplier.create(findbugs, new FindBugsReportHandler()),
                                new File(circleReportsDir, "findbugs"));
                    }
                });
                project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
                    @Override
                    public void execute(JavaCompile javac) {
                        registerFinalizer(
                                javac,
                                timer,
                                JavacFailuresSupplier.create(javac),
                                new File(circleReportsDir, "javac"));
                    }
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void setDestination(ConfigurableReport report, File destination) {
        try {
            report.setDestination(destination);
        } catch (NoSuchMethodError e) {
            // Fall back to pre-Gradle 4 method
            report.setDestination((Object) destination);
        }
    }

    private static void configureBuildFailureFinalizer(Project rootProject, String circleReportsDir) {
        int attemptNumber = 1;
        File targetFile = new File(new File(circleReportsDir, "gradle"), "build.xml");
        while (targetFile.exists()) {
            targetFile = new File(new File(circleReportsDir, "gradle"), "build" + (++attemptNumber) + ".xml");
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
