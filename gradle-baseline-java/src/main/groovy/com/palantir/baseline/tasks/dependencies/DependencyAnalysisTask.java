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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

/**
 * Produces report to optimize dependencies for a project.
 *
 * Configurations are passed by name rather than the full configuration themselves because the caching calculations
 * attempt to resolve the configurations.  This leads to errors with configs that cannot be resolved at configuration
 * time, notably implementation and api.
 *
 * The classpathConfiguration is also passed in order to give the most complete set of dependency artifacts to search
 * when looking up a class.  Using it also avoids some errors under Gradle 5.x because it can better access classes
 * in project-dependencies than the other configurations can.
 */
@CacheableTask
public class DependencyAnalysisTask extends DefaultTask {
    private final SetProperty<String> configurations;
    private final Property<Configuration> classpathConfiguration;
    private final DirectoryProperty dotFileDir;
    private final RegularFileProperty report;

    public DependencyAnalysisTask() {
        configurations = getProject().getObjects().setProperty(String.class);
        configurations.convention(Collections.emptyList());
        classpathConfiguration = getProject().getObjects().property(Configuration.class);

        dotFileDir = getProject().getObjects().directoryProperty();

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
                classpathConfiguration.get(),
                dotFileDir.get());
        analyzer.analyze();

        ReportContent content = new ReportContent();
        content.allDependencies = sortDependencies(analyzer.getAllRequiredDependencies());
        content.apiDependencies = sortDependencies(analyzer.getApiDependencies());
        content.implicitDependencies = sortDependencies(analyzer.getImplicitDependencies());
        content.unusedDependencies = sortDependencies(analyzer.getUnusedDependencies());

        DependencyUtils.writeDepReport(getReportFile().getAsFile().get(), content);

    }

    private List<String> sortDependencies(Collection<String> dependencyIds) {
        return dependencyIds.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Names of configurations whose dependencies will be analyzed.
     * @return
     */
    @Input
    public final SetProperty<String> getConfigurations() {
        return configurations;
    }

    /**
     * The configuration used to generate the compile classpath for the classes being analyzed.  This is normally
     * a superset of the configurations being analyzed.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Property<Configuration> getClasspathConfiguration() {
        return classpathConfiguration;
    }

    /**
     * Directory containing dot-file reports generated by the DependencyFinderTask.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public final DirectoryProperty getDotFileDir() {
        return dotFileDir;
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
