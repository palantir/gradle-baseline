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

package com.palantir.baseline.tasks;

import com.palantir.baseline.tasks.dependencies.DependencyReportTask;
import com.palantir.baseline.tasks.dependencies.DependencyUtils;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public class CheckImplicitDependenciesTask extends DefaultTask {

    private final RegularFileProperty report;
    private final SetProperty<String> ignored;

    public CheckImplicitDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures all dependencies are explicitly declared, not just transitively provided");
        report = getProject().getObjects().fileProperty();

        ignored = getProject().getObjects().setProperty(String.class);
        ignored.convention(Collections.emptySet());
    }

    @TaskAction
    public final void checkImplicitDependencies() {
        DependencyReportTask.ReportContent reportContent =
                DependencyUtils.getReportContent(getReportFile().getAsFile().get());

        if (reportContent.getImplicitDependencies().isEmpty()) {
            return;
        }

        List<String> implicitDependencies = reportContent.getImplicitDependencies().stream()
                .filter(artifact -> !shouldIgnore(artifact))
                .collect(Collectors.toList());

        if (!implicitDependencies.isEmpty()) {
            String suggestion = implicitDependencies.stream()
                    .map(artifact -> DependencyUtils.getSuggestionString(artifact))
                    .sorted()
                    .collect(Collectors.joining("\n", "    dependencies {\n", "\n    }"));

            throw new GradleException(
                    String.format("Found %d implicit dependencies - consider adding the following explicit "
                            + "dependencies to '%s', or avoid using classes from these jars:\n%s",
                            implicitDependencies.size(),
                    buildFile(),
                    suggestion));
        }
    }

    private Path buildFile() {
        return getProject().getRootDir().toPath().relativize(getProject().getBuildFile().toPath());
    }

    private boolean shouldIgnore(String artifact) {
        return ignored.get().contains(artifact);
    }

    /**
     * @return Dependency report location.
     */
    @InputFile
    public RegularFileProperty getReportFile() {
        return report;
    }

    @Input
    @Optional
    public final SetProperty<String> getIgnored() {
        return ignored;
    }
}
