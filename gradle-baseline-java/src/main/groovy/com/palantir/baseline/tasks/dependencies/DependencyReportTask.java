/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.baseline.tasks.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.Yaml;

@CacheableTask
public class DependencyReportTask extends DefaultTask {
    private final ListProperty<Configuration> configurations;
    private final ConfigurableFileCollection fullDepFiles;
    private final ConfigurableFileCollection apiDepFiles;
    private final SetProperty<String> ignore;
    private final RegularFileProperty report;

    public DependencyReportTask() {
        configurations = getProject().getObjects().listProperty(Configuration.class);
        configurations.set(Collections.emptyList());
        fullDepFiles = getProject().getObjects().fileCollection();
        apiDepFiles = getProject().getObjects().fileCollection();
        ignore = getProject().getObjects().setProperty(String.class);
        ignore.set(Collections.emptySet());
        report = getProject().getObjects().fileProperty();
        RegularFileProperty defaultReport = getProject().getObjects().fileProperty();
        defaultReport.set(
                getProject().file(String.format("%s/reports/%s-report.yaml", getProject().getBuildDir(), getName())));
        report.convention(defaultReport);
    }

    @TaskAction
    public final void analyzeDependencies() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(getProject(),
                configurations.get(),
                fullDepFiles,
                apiDepFiles,
                ignore.get());

        ReportContent content = new ReportContent();
        content.allDependencies = artifactNames(analyzer.getAllRequiredArtifacts());
        content.apiDependencies = artifactNames(analyzer.getApiArtifacts());
        content.implicitDependencies = artifactNames(analyzer.getImplicitDependencies());
        content.unusedDependencies = artifactNames(analyzer.getUnusedDependencies());

        Yaml yaml = new Yaml();
        try {
            File file = getReportFile().getAsFile().get();
            String contents = yaml.dumpAsMap(content);
            Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Error writing dependency report", e);
        }

    }

    private List<String> artifactNames(Collection<ResolvedArtifact> artifacts) {
        return artifacts.stream()
                .map(DependencyUtils::getArtifactName)
                .sorted()
                .collect(Collectors.toList());
    }

    public final ListProperty<Configuration> getConfigurations() {
        return configurations;
    }

    @InputFiles
    public final ConfigurableFileCollection getFullDepFiles() {
        return fullDepFiles;
    }

    @InputFiles
    @Optional
    public final ConfigurableFileCollection getApiDepFiles() {
        return apiDepFiles;
    }

    @Input
    @Optional
    public final SetProperty<String> getIgnored() {
        return ignore;
    }

    @OutputFile
    public final RegularFileProperty getReportFile() {
        return report;
    }

    public static final class ReportContent {
        private List<String> allDependencies;
        private List<String> apiDependencies;
        private List<String> implicitDependencies;
        private List<String> unusedDependencies;

        public List<String> getAllDependencies() {
            return allDependencies;
        }

        public List<String> getApiDependencies() {
            return apiDependencies;
        }

        public List<String> getImplicitDependencies() {
            return implicitDependencies;
        }

        public List<String> getUnusedDependencies() {
            return unusedDependencies;
        }

        public void setAllDependencies(List<String> allDependencies) {
            this.allDependencies = allDependencies;
        }

        public void setApiDependencies(List<String> apiDependencies) {
            this.apiDependencies = apiDependencies;
        }

        public void setImplicitDependencies(List<String> implicitDependencies) {
            this.implicitDependencies = implicitDependencies;
        }

        public void setUnusedDependencies(List<String> unusedDependencies) {
            this.unusedDependencies = unusedDependencies;
        }
    }
}
