/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.gradle.junit.TestCommon.FAILED_CHECKSTYLE_TIME_NANOS;
import static com.palantir.gradle.junit.TestCommon.ROOT;
import static com.palantir.gradle.junit.TestCommon.readTestFile;
import static com.palantir.gradle.junit.TestCommon.testFile;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.TransformerException;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JunitReportsFinalizerTests {

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder();

    @Test
    public void translatesCheckstyleReport() throws IOException, TransformerException {
        Project project = ProjectBuilder.builder()
                .withName("fooproject")
                .withProjectDir(projectDir.getRoot())
                .build();
        Checkstyle checkstyle = createCheckstyleTask(project);

        checkstyle.setDidWork(true);

        File targetFile = new File(projectDir.getRoot(), "reports/report.xml");

        JunitReportsFinalizer finalizer = (JunitReportsFinalizer)
                project.task(ImmutableMap.of("type", JunitReportsFinalizer.class), "checkstyleTestCircleFinalizer");
        finalizer.getWrappedDidWork().set(true);
        finalizer.getWrappedTaskName().set(checkstyle.getName());
        finalizer.getDurationNanos().set(FAILED_CHECKSTYLE_TIME_NANOS);
        finalizer.setFailuresSupplier(XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()));
        finalizer.getTargetFile().set(targetFile);

        finalizer.createCircleReport();

        String report = Resources.toString(targetFile.toURI().toURL(), StandardCharsets.UTF_8)
                .replaceAll("\\p{Blank}*(?=<)", "");
        String expectedReport =
                Resources.toString(testFile("two-namecheck-failures-checkstyle-report.xml"), StandardCharsets.UTF_8);

        assertThat(report).isEqualTo(expectedReport);
    }

    @Test
    public void doesNothingIfTaskSkipped() throws IOException, TransformerException {
        Project project = ProjectBuilder.builder()
                .withName("fooproject")
                .withProjectDir(projectDir.getRoot())
                .build();
        Checkstyle checkstyle = createCheckstyleTask(project);

        File targetFile = new File(projectDir.getRoot(), "reports/report.xml");

        JunitReportsFinalizer finalizer = (JunitReportsFinalizer)
                project.task(ImmutableMap.of("type", JunitReportsFinalizer.class), "checkstyleTestCircleFinalizer");
        finalizer.getWrappedDidWork().set(false);
        finalizer.getWrappedTaskName().set(checkstyle.getName());
        finalizer.getDurationNanos().set(FAILED_CHECKSTYLE_TIME_NANOS);
        finalizer.setFailuresSupplier(XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()));
        finalizer.getTargetFile().set(targetFile);

        finalizer.createCircleReport();

        assertThat(targetFile).doesNotExist();
        assertThat(finalizer.getDidWork()).isFalse();
    }

    private Checkstyle createCheckstyleTask(Project project) throws IOException {
        Checkstyle checkstyle = project.getTasks().create("checkstyleTest", Checkstyle.class);
        SingleFileReport xmlReport = checkstyle.getReports().getByName("xml");

        String originalReportXml = readTestFile("two-namecheck-failures-checkstyle.xml");
        String modifiedReportXml =
                originalReportXml.replace(ROOT.toString(), projectDir.getRoot().getCanonicalPath());
        File modifiedReportFile = projectDir.newFile();
        Files.write(modifiedReportXml.getBytes(StandardCharsets.UTF_8), modifiedReportFile);

        xmlReport.getOutputLocation().set(modifiedReportFile);
        return checkstyle;
    }
}
