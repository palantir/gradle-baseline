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

package com.palantir.baseline.plugins.rules;

import com.google.common.collect.ImmutableList;
import com.palantir.baseline.tasks.CheckClassUniquenessTask;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.api.artifacts.Configuration;

public final class BaselineClassUniquenessRule implements Rule {
    private static final Pattern TASK_NAME_PATTERN = Pattern.compile("^check(.*)ClassUniqueness$");
    private final Project project;

    public BaselineClassUniquenessRule(Project project) {
        this.project = project;
    }

    @Override
    public String getDescription() {
        return "Pattern: check<ID>ClassUniqueness";
    }

    @Override
    public void apply(String taskName) {
        Matcher taskNameMatcher = TASK_NAME_PATTERN.matcher(taskName);

        if (taskNameMatcher.matches()) {
            String confName = taskNameMatcher.group(1);
            String altName = confName.substring(0, 1).toLowerCase() + confName.substring(1);

            List<String> candidates = ImmutableList.of(altName, confName);

            Optional<Configuration> configOpt = candidates.stream()
                    .map(project.getConfigurations()::findByName)
                    .filter(Objects::nonNull)
                    .findFirst();

            if (configOpt.isPresent()) {
                project.getTasks().create(taskName, CheckClassUniquenessTask.class, task -> {
                    task.setConfiguration(configOpt.get());
                });
            } else {
                throw new GradleException("Couldn't find configuration " + altName);
            }
        }
    }
}
