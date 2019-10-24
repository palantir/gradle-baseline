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

public class CheckUnusedDependenciesTaskv2 extends DefaultTask {

    private final RegularFileProperty report;
    private final SetProperty<String> ignored;

    public CheckUnusedDependenciesTaskv2() {
        report = getProject().getObjects().fileProperty();

        ignored = getProject().getObjects().setProperty(String.class);
        ignored.convention(Collections.emptySet());
    }

    @TaskAction
    public final void checkUnusedDependencies() {
        DependencyReportTask.ReportContent reportContent =
                DependencyUtils.getReportContent(getReportFile().getAsFile().get());

        if (reportContent.getUnusedDependencies().isEmpty()) {
            return;
        }

        List<String> declaredButUnused = reportContent.getUnusedDependencies().stream()
                .filter(artifact -> !shouldIgnore(artifact))
                .collect(Collectors.toList());

        getLogger().debug("Possibly unused dependencies: {}", declaredButUnused);
        if (!declaredButUnused.isEmpty()) {
            // TODO(dfox): don't print warnings for jars that define service loaded classes (e.g. meta-inf)
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %s dependencies unused during compilation, please delete them from '%s' or "
                    + "choose one of the suggested fixes:\n", declaredButUnused.size(), buildFile()));
            for (String resolvedArtifact : declaredButUnused) {
                sb.append('\t').append(resolvedArtifact).append('\n');

                // Suggest fixes from the implicit list
                List<String> didYouMean = reportContent.getImplicitDependencies();

                if (!didYouMean.isEmpty()) {
                    sb.append("\t\tDid you mean:\n");
                    didYouMean.stream()
                            .map(s -> "\t\t\t" + DependencyUtils.getSuggestionString(s))
                            .forEach(transitive -> sb.append(transitive).append("\n"));
                }
            }
            throw new GradleException(sb.toString());
        }
    }

    private Path buildFile() {
        return getProject().getRootDir().toPath().relativize(getProject().getBuildFile().toPath());
    }

    private boolean shouldIgnore(String artifact) {
        return ignored.get().contains(artifact);
    }

    /**
     * Dependency report location.
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
