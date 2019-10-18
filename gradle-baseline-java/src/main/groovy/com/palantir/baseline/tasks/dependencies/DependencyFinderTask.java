/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.baseline.tasks.dependencies;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

@CacheableTask
public class DependencyFinderTask extends DefaultTask {
    private final DirectoryProperty sourceClasses;
    private final DirectoryProperty reportDir;
    private final RegularFileProperty reportFile;
    private final Property<Boolean> apiOnly;
    private final SetProperty<String> ignored;

    public DependencyFinderTask() {
        sourceClasses = getProject().getObjects().directoryProperty();

        apiOnly = getProject().getObjects().property(Boolean.class);
        apiOnly.convention(false);

        reportDir = getProject().getObjects().directoryProperty();
        reportDir.convention(getProject().getLayout().getBuildDirectory()
                .dir(String.format("reports/dep-dot-files/%s", getName())));

        //jdeps will name the dot file after the directory where it read classes from
        reportFile = getProject().getObjects().fileProperty();
        reportFile.value(reportDir.file(sourceClasses.map(s -> s.getAsFile().getName() + ".dot")));

        //by default will not report classes in the JDK
        ignored = getProject().getObjects().setProperty(String.class);
        SetProperty<String> defaultFilter = getProject().getObjects().setProperty(String.class);
        defaultFilter.set(ImmutableSet.<String>of("java.*"));
        ignored.convention(defaultFilter);
    }

    @TaskAction
    protected void exec() {
        //invoke jdeps
        final List<String> command = new ArrayList<>();
        command.add("jdeps");
        command.add("-verbose:class");
        command.add("-dotoutput");
        command.add(reportDir.getAsFile().get().getAbsolutePath());
        if (apiOnly.get()) {
            command.add("-apionly");
        }
        ignored.get().forEach(s -> {
            command.add("-f");
            command.add(s);
        });
        command.add(sourceClasses.get().getAsFile().getAbsolutePath());

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
     * Directory of class files to parse
     */
    @InputDirectory
    public final DirectoryProperty getSourceClasses() {
        return sourceClasses;
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
     * Whether to pass apionly flag to jdeps.  See jdeps documentation for more details.
     */
    @Input
    @Optional
    public Property<Boolean> getApiOnly() {
        return apiOnly;
    }

    /**
     * Directory where report will be written.
     */
    public final DirectoryProperty getReportDir() {
        return reportDir;
    }

    /**
     * This is a Provider rather than a property because it cannot be changed.  The report
     * file name is fixed by jdeps and the path is set by the reportDir property
     */
    @OutputFile
    public final Provider<RegularFile> getReportFile() {
        return reportFile;
    }
}
