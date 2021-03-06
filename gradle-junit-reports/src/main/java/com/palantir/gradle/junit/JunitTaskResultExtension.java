/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.immutables.value.Value.Immutable;

public class JunitTaskResultExtension {
    static final String NAME = "junitTaskResults";
    private final NamedDomainObjectSet<TaskEntry> taskEntries;

    static JunitTaskResultExtension register(Project project) {
        return project.getExtensions().create(NAME, JunitTaskResultExtension.class);
    }

    public JunitTaskResultExtension(ObjectFactory objects) {
        this.taskEntries = objects.namedDomainObjectSet(TaskEntry.class);
    }

    public final NamedDomainObjectSet<TaskEntry> getTaskEntries() {
        return taskEntries;
    }

    public final void registerTask(String taskName, Provider<List<Failure>> failures) {
        registerTask(taskName, new FailuresSupplier() {
            @Override
            public List<Failure> getFailures() {
                return failures.get();
            }

            @Override
            public RuntimeException handleInternalFailure(Path _reportDir, RuntimeException ex) {
                return ex;
            }
        });
    }

    public final void registerTask(String taskName, FailuresSupplier failuresSupplier) {
        taskEntries.add(TaskEntry.of(taskName, failuresSupplier));
    }

    @Immutable
    interface TaskEntry {

        String name();

        FailuresSupplier failuresSupplier();

        static TaskEntry of(String name, FailuresSupplier failuresSupplier) {
            return ImmutableTaskEntry.builder()
                    .name(name)
                    .failuresSupplier(failuresSupplier)
                    .build();
        }
    }
}
