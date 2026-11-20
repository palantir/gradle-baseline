/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.baseline.plugins.BaselineJavaCompilerHeap.PeriodicGcArgumentProvider;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class PeriodicGcArgumentProviderTest {

    private final Project project = ProjectBuilder.builder().build();

    @Test
    void omits_flag_for_jdks_without_it() {
        assertThat(argumentsFor(8)).isEmpty();
        assertThat(argumentsFor(11)).isEmpty();
    }

    @Test
    void adds_flag_from_jdk_12() {
        assertThat(argumentsFor(12)).containsExactly(PeriodicGcArgumentProvider.FLAG);
        assertThat(argumentsFor(25)).containsExactly(PeriodicGcArgumentProvider.FLAG);
    }

    private Iterable<String> argumentsFor(int javaVersion) {
        return new PeriodicGcArgumentProvider(project.provider(() -> javaVersion)).asArguments();
    }
}
