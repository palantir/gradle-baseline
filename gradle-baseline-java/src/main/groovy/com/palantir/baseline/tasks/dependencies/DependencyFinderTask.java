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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

@CacheableTask
public class DependencyFinderTask extends DefaultTask {
    private final DirectoryProperty classesDir;
    private final ConfigurableFileCollection classPath;
    private final DirectoryProperty reportDir;
    private final SetProperty<String> ignored;

    public DependencyFinderTask() {
        classesDir = getProject().getObjects().directoryProperty();
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
        List<String> baseCommand = new ArrayList<>();
        baseCommand.add("jdeps");
        baseCommand.add("-verbose:class");
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
        fullCommand.add(reportDir.getAsFile().get().getAbsolutePath());
        fullCommand.add(classesDir.get().getAsFile().getAbsolutePath());
        runJDeps(fullCommand);

        //run the command for APIs.  Put the reports in a sub-dir
        List<String> apiCommand = new ArrayList<>(baseCommand);
        apiCommand.add("-dotoutput");
        apiCommand.add(reportDir.dir("api").get().getAsFile().getAbsolutePath());
        apiCommand.add("-apionly");
        apiCommand.add(classesDir.get().getAsFile().getAbsolutePath());
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
     * Directory of class files to parse.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final DirectoryProperty getClassesDir() {
        return classesDir;
    }

    /**
     * Optional class path used to compile the classes.  jdeps can use this to report what jar something came from
     * @return
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
    @OutputFiles
    public final DirectoryProperty getReportDir() {
        return reportDir;
    }
}
