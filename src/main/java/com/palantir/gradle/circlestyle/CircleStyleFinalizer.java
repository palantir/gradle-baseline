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

import static com.palantir.gradle.circlestyle.FailuresReportGenerator.failuresReport;
import static com.palantir.gradle.circlestyle.JUnitReportCreator.reportToXml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.xml.transform.TransformerException;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;

class CircleStyleFinalizer extends DefaultTask {

    public static void registerFinalizer(
            Task task,
            StyleTaskTimer timer,
            FailuresSupplier failuresSupplier,
            File reportDir) {
        CircleStyleFinalizer finalizer = Tasks.createTask(
                task.getProject().getTasks(),
                task.getName() + "CircleFinalizer",
                CircleStyleFinalizer.class);
        if (finalizer == null) {
            // Already registered (happens if the user applies us to the root project and subprojects)
            return;
        }
        finalizer.setStyleTask(task);
        finalizer.setStyleTaskTimer(timer);
        finalizer.setFailuresSupplier(failuresSupplier);
        finalizer.setTargetFile(new File(reportDir, task.getProject().getName() + "-" + task.getName() + ".xml"));
        finalizer.setReportDir(reportDir);

        task.finalizedBy(finalizer);
    }

    private Task styleTask;
    private StyleTaskTimer styleTaskTimer;
    private FailuresSupplier failuresSupplier;
    private File targetFile;
    private File reportDir;

    @Inject
    public CircleStyleFinalizer() { }

    public Task getStyleTask() {
        return styleTask;
    }

    public void setStyleTask(Task styleTask) {
        this.styleTask = styleTask;
    }

    public StyleTaskTimer getStyleTaskTimer() {
        return styleTaskTimer;
    }

    public void setStyleTaskTimer(StyleTaskTimer styleTaskTimer) {
        this.styleTaskTimer = styleTaskTimer;
    }

    public FailuresSupplier getFailuresSupplier() {
        return failuresSupplier;
    }

    public void setFailuresSupplier(FailuresSupplier failuresSupplier) {
        this.failuresSupplier = failuresSupplier;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }

    public File getReportDir() {
        return reportDir;
    }

    public void setReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    @TaskAction
    public void createCircleReport() throws IOException, TransformerException {
        if (!styleTask.getDidWork()) {
            setDidWork(false);
            return;
        }

        try {
            File rootDir = getProject().getRootProject().getProjectDir();
            String projectName = getProject().getName();
            List<Failure> failures = failuresSupplier.getFailures();
            long taskTimeNanos = styleTaskTimer.getTaskTimeNanos(styleTask);

            Document report = reportToXml(failuresReport(
                    rootDir, projectName, styleTask.getName(), taskTimeNanos, failures));
            targetFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(targetFile)) {
                XmlUtils.write(writer, report);
            }
        } catch (RuntimeException e) {
            RuntimeException modified;
            try {
                modified = failuresSupplier.handleInternalFailure(reportDir, e);
            } catch (RuntimeException x) {
                e.addSuppressed(x);
                throw e;
            }
            throw modified;
        }
    }
}
