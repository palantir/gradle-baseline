/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.junit;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.inject.Inject;
import javax.xml.transform.TransformerException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;

public class JunitReportsFinalizer extends DefaultTask {

    public static void registerFinalizer(
            Task task, TaskTimer timer, FailuresSupplier failuresSupplier, Provider<Directory> reportDir) {
        JunitReportsFinalizer finalizer = Tasks.createTask(
                task.getProject().getTasks(), task.getName() + "CircleFinalizer", JunitReportsFinalizer.class);
        if (finalizer == null) {
            // Already registered (happens if the user applies us to the root project and subprojects)
            return;
        }
        finalizer.setStyleTask(task);
        finalizer.setTaskTimer(timer);
        finalizer.setFailuresSupplier(failuresSupplier);
        finalizer.getTargetFile().set(reportDir.map(dir ->
                dir.file(task.getProject().getName() + "-" + task.getName() + ".xml")));
        finalizer.getReportDir().set(reportDir);

        task.finalizedBy(finalizer);
    }

    private Task styleTask;
    private TaskTimer taskTimer;
    private FailuresSupplier failuresSupplier;
    private final RegularFileProperty targetFile = getProject().getObjects().fileProperty();
    private final DirectoryProperty reportDir = getProject().getObjects().directoryProperty();

    @Inject
    public JunitReportsFinalizer() {}

    public final Task getStyleTask() {
        return styleTask;
    }

    public final void setStyleTask(Task styleTask) {
        this.styleTask = styleTask;
    }

    public final TaskTimer getTaskTimer() {
        return taskTimer;
    }

    public final void setTaskTimer(TaskTimer taskTimer) {
        this.taskTimer = taskTimer;
    }

    public final FailuresSupplier getFailuresSupplier() {
        return failuresSupplier;
    }

    public final void setFailuresSupplier(FailuresSupplier failuresSupplier) {
        this.failuresSupplier = failuresSupplier;
    }

    public final RegularFileProperty getTargetFile() {
        return targetFile;
    }

    public final DirectoryProperty getReportDir() {
        return reportDir;
    }

    @TaskAction
    public final void createCircleReport() throws IOException, TransformerException {
        if (!styleTask.getDidWork()) {
            setDidWork(false);
            return;
        }

        try {
            File rootDir = getProject().getRootProject().getProjectDir();
            String projectName = getProject().getName();
            List<Failure> failures = failuresSupplier.getFailures();
            long taskTimeNanos = taskTimer.getTaskTimeNanos(styleTask);

            Document report = JunitReportCreator.reportToXml(FailuresReportGenerator.failuresReport(
                    rootDir, projectName, styleTask.getName(), taskTimeNanos, failures));

            File target = targetFile.getAsFile().get();
            Files.createDirectories(target.getParentFile().toPath());
            try (Writer writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                XmlUtils.write(writer, report);
            }
        } catch (RuntimeException e) {
            RuntimeException modified;
            try {
                modified = failuresSupplier.handleInternalFailure(
                        reportDir.getAsFile().get().toPath(), e);
            } catch (RuntimeException x) {
                e.addSuppressed(x);
                throw e;
            }
            throw modified;
        }
    }
}
