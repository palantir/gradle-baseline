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

package com.palantir.baseline.tasks.dependencies;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

@CacheableTask
public class DependencyFinderTask extends DefaultTask {
    private final ConfigurableFileCollection classesDirs;
    private final ConfigurableFileCollection classPath;
    private final DirectoryProperty reportDir;
    private final SetProperty<String> ignored;

    public DependencyFinderTask() {
        classesDirs = getProject().getObjects().fileCollection();
        classPath = getProject().getObjects().fileCollection();

        reportDir = getProject().getObjects().directoryProperty();
        reportDir.convention(getProject().getLayout().getBuildDirectory()
                .dir(String.format("reports/dep-dot-files/%s", getName())));

        //do not report classes in the JDK by default
        ignored = getProject().getObjects().setProperty(String.class);
        SetProperty<String> defaultFilter = getProject().getObjects().setProperty(String.class);
        defaultFilter.set(ImmutableSet.<String>of("java.*"));
        ignored.convention(defaultFilter);

    }

    /**
     * Run jdeps for both full set of dependencies and just APIs.
     */
    @TaskAction
    protected void exec() {

        File outputDir = reportDir.getAsFile().get();
        Set<File> dirs = classesDirs.getFiles();

        if (classesDirs.isEmpty() || dirs.size() == 0) {
            writeStubFile(outputDir, "No classes directories set");
            return;
        }
        if (dirs.size() > 1) {
            getLogger().warn("More than one classes directory set.  Only the first will be analyzed.");
        }

        File classDir = dirs.stream().findFirst().get();
        String classesPath = classDir.getAbsolutePath();
        if (!classDir.exists()) {
            writeStubFile(outputDir, "Classes directory does not exist: " + classesPath);
            return;
        }

        List<String> baseCommand = new ArrayList<>();
        baseCommand.add("jdeps");
        baseCommand.add("-v");
        ignored.get().forEach(s -> {
            baseCommand.add("-f");
            baseCommand.add(s);
        });
        if (!classPath.isEmpty()) {
            baseCommand.add("-cp");
            baseCommand.add(classPath.getAsPath());
        }

        List<String> fullCommand = new ArrayList<>(baseCommand);
        fullCommand.add("-dotoutput");
        fullCommand.add(outputDir.getAbsolutePath());
        fullCommand.add(classesPath);
        runJDeps(fullCommand);

        //run the command for APIs.  Put the reports in a sub-dir
        List<String> apiCommand = new ArrayList<>(baseCommand);
        apiCommand.add("-dotoutput");
        apiCommand.add(reportDir.dir("api").get().getAsFile().getAbsolutePath());
        apiCommand.add("-apionly");
        apiCommand.add(classesPath);
        runJDeps(apiCommand);
    }

    private void runJDeps(List<String> command) {
        getProject().getLogger().info("Calling jdeps with command line: " + command);
        try {
            new ProcessExecutor().command(command)
                    .readOutput(true)
                    .exitValues(0)
                    .execute()
                    .outputUTF8();
        } catch (InvalidExitValueException e) {
            throw new RuntimeException(
                    "Error running jdeps: " + e.getExitValue() + " Output: " + e.getResult().outputUTF8(),
                    e);
        } catch (InterruptedException | IOException | TimeoutException e) {
            throw new RuntimeException("Error running jdeps", e);
        }
    }

    /**
     * Write a useful file if no processing takes place.
     */
    private void writeStubFile(File outputDir, String message) {
        outputDir.mkdirs();
        File emptyFile = new File(outputDir, "summary.dot");
        String contents = "digraph \"summary\" {\n//" + message + "\n}\n";

        try {
            Files.write(emptyFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            getProject().getLogger().warn("Could not write stub file", e);
        }
    }

    /**
     * Directory of class files to parse.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public final ConfigurableFileCollection getClassesDirs() {
        return classesDirs;
    }

    /**
     * Optional class path used to compile the classes.  jdeps can use this to report what jar something came from
     */
    @InputFiles
    @Classpath
    @Optional
    public ConfigurableFileCollection getClassPath() {
        return classPath;
    }

    /**
     * Regex patterns to filter using jdeps "-f" option.
     */
    @Input
    @Optional
    public final SetProperty<String> getIgnored() {
        return ignored;
    }

    /**
     * Directory where reports will be written.
     */
    @OutputDirectory
    public final DirectoryProperty getReportDir() {
        return reportDir;
    }
}
