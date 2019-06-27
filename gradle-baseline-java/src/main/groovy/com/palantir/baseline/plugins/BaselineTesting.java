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

package com.palantir.baseline.plugins;

import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.testing.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineTesting implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(BaselineTesting.class);
    private final AtomicBoolean junit5 = new AtomicBoolean(false);

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class).all(task -> {
            task.jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CrashOnOutOfMemoryError");
        });

        project.afterEvaluate(proj -> {
            Configuration configuration = project.getConfigurations().getByName("testRuntimeClasspath");
            configuration.getIncoming().getDependencies()
                    .matching(dep -> dep.getGroup().equals("org.junit.jupiter") && dep.getName().equals("junit-jupiter"))
                    .all(dep -> {
                        log.info("Detected 'org:junit.jupiter:junit-jupiter', enabling useJUnitPlatform()");
                        enableJUnit5ForAllTestTasks(project);
                    });
        });
    }

    private void enableJUnit5ForAllTestTasks(Project project) {
        if (junit5.compareAndSet(false, true)) {
            project.getTasks().withType(Test.class).configureEach(task -> {
                task.useJUnitPlatform();

                task.systemProperty("junit.platform.output.capture.stdout", "true");
                task.systemProperty("junit.platform.output.capture.stderr", "true");

                // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
                task.systemProperty("junit.jupiter.execution.parallel.enabled", "true");

                // Computes the desired parallelism based on the number of available processors/cores
                task.systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic");

                // the factor to be multiplied with the number of available processors/cores to determine the desired
                // parallelism for the dynamic configuration strategy.
                task.systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", "1");
            });
        }
    }
}
