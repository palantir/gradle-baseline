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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.xml.transform.TransformerException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.w3c.dom.Document;

public abstract class JunitReportsFinalizer extends DefaultTask {

    public static void registerFinalizer(
            Project project,
            String taskName,
            TaskTimer taskTimer,
            FailuresSupplier failuresSupplier,
            Provider<Directory> reportDir) {
        TaskProvider<Task> wrappedTask = project.getTasks().named(taskName);
        TaskProvider<JunitReportsFinalizer> finalizer = project.getTasks()
                .register(taskName + "JunitReportsFinalizer", JunitReportsFinalizer.class, task -> {
                    task.getWrappedDidWork()
                            .set(project.provider(() -> wrappedTask.get().getDidWork()));
                    task.getWrappedTaskName().set(taskName);
                    task.getDurationNanos().set(project.provider(() -> taskTimer.getTaskTimeNanos(wrappedTask.get())));
                    task.setFailuresSupplier(failuresSupplier);
                    task.getTargetFile()
                            .set(reportDir.map(dir -> dir.file(project.getName() + "-" + taskName + ".xml")));
                    task.getReportDir().set(reportDir);
                });

        wrappedTask.configure(task -> {
            task.finalizedBy(finalizer);
        });
    }

    private FailuresSupplier failuresSupplier;

    @Input
    public abstract Property<Boolean> getWrappedDidWork();

    @Input
    abstract Property<String> getWrappedTaskName();

    @Internal
    public final FailuresSupplier getFailuresSupplier() {
        return failuresSupplier;
    }

    public final void setFailuresSupplier(FailuresSupplier failuresSupplier) {
        this.failuresSupplier = failuresSupplier;
    }

    @Internal
    public abstract Property<Long> getDurationNanos();

    @OutputFile
    public abstract RegularFileProperty getTargetFile();

    @Internal
    public abstract DirectoryProperty getReportDir();

    @TaskAction
    public final void createCircleReport() throws IOException, TransformerException {
        if (!getWrappedDidWork().get()) {
            setDidWork(false);
            return;
        }

        try {
            File rootDir = getProject().getRootProject().getProjectDir();
            String projectName = getProject().getName();
            List<Failure> failures = failuresSupplier.getFailures();
            long taskTimeNanos = getDurationNanos().get();

            Document report = JunitReportCreator.reportToXml(FailuresReportGenerator.failuresReport(
                    rootDir, projectName, getWrappedTaskName().get(), taskTimeNanos, failures));

            File target = getTargetFile().getAsFile().get();
            Files.createDirectories(target.getParentFile().toPath());
            try (Writer writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                XmlUtils.write(writer, report);
            }
        } catch (RuntimeException e) {
            RuntimeException modified;
            try {
                modified = failuresSupplier.handleInternalFailure(
                        getReportDir().getAsFile().get().toPath(), e);
            } catch (RuntimeException x) {
                e.addSuppressed(x);
                throw e;
            }
            throw modified;
        }
    }
}
