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

import static com.google.common.base.Charsets.UTF_8;
import static com.palantir.gradle.circlestyle.TestCommon.FAILED_CHECKSTYLE_TIME_NANOS;
import static com.palantir.gradle.circlestyle.TestCommon.ROOT;
import static com.palantir.gradle.circlestyle.TestCommon.readTestFile;
import static com.palantir.gradle.circlestyle.TestCommon.testFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.gradle.api.Project;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.internal.impldep.com.google.common.io.Files;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

public class CircleStyleFinalizerTests {

    @Rule public final TemporaryFolder projectDir = new TemporaryFolder();

    @Test
    public void translatesCheckstyleReport() throws IOException, TransformerException {
        Project project = ProjectBuilder.builder().withName("fooproject").withProjectDir(projectDir.getRoot()).build();
        Checkstyle checkstyle = createCheckstyleTask(project);

        checkstyle.setDidWork(true);

        StyleTaskTimer timer = mock(StyleTaskTimer.class);
        when(timer.getTaskTimeNanos(checkstyle)).thenReturn(FAILED_CHECKSTYLE_TIME_NANOS);

        File targetFile = new File(projectDir.getRoot(), "reports/report.xml");

        CircleStyleFinalizer finalizer = (CircleStyleFinalizer) project
                .task(ImmutableMap.of("type", CircleStyleFinalizer.class), "checkstyleTestCircleFinalizer");
        finalizer.setStyleTask(checkstyle);
        finalizer.setStyleTaskTimer(timer);
        finalizer.setFailuresSupplier(XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()));
        finalizer.setTargetFile(targetFile);

        finalizer.createCircleReport();

        String report = Resources.toString(targetFile.toURI().toURL(), UTF_8);
        String expectedReport = Resources.toString(testFile("two-namecheck-failures-checkstyle-report.xml"), UTF_8);

        assertThat(report).isEqualTo(expectedReport);
    }

    @Test
    public void doesNothingIfTaskSkipped() throws IOException, TransformerException {
        Project project = ProjectBuilder.builder().withName("fooproject").withProjectDir(projectDir.getRoot()).build();
        Checkstyle checkstyle = createCheckstyleTask(project);

        checkstyle.setDidWork(false);

        StyleTaskTimer timer = mock(StyleTaskTimer.class);
        when(timer.getTaskTimeNanos(checkstyle)).thenReturn(FAILED_CHECKSTYLE_TIME_NANOS);

        File targetFile = new File(projectDir.getRoot(), "reports/report.xml");

        CircleStyleFinalizer finalizer = (CircleStyleFinalizer) project
                .task(ImmutableMap.of("type", CircleStyleFinalizer.class), "checkstyleTestCircleFinalizer");
        finalizer.setStyleTask(checkstyle);
        finalizer.setStyleTaskTimer(timer);
        finalizer.setFailuresSupplier(XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()));
        finalizer.setTargetFile(targetFile);

        finalizer.createCircleReport();

        assertThat(targetFile).doesNotExist();
        assertThat(finalizer.getDidWork()).isFalse();
    }

    private Checkstyle createCheckstyleTask(Project project) throws IOException {
        Checkstyle checkstyle = project.getTasks().create("checkstyleTest", Checkstyle.class);
        SingleFileReport xmlReport = checkstyle.getReports().getByName("xml");

        String originalReportXml = readTestFile("two-namecheck-failures-checkstyle.xml");
        String modifiedReportXml = originalReportXml.replace(ROOT.toString(), projectDir.getRoot().getCanonicalPath().toString());
        File modifiedReportFile = projectDir.newFile();
        Files.write(modifiedReportXml, modifiedReportFile, UTF_8);

        xmlReport.setDestination(modifiedReportFile);
        return checkstyle;
    }
}
