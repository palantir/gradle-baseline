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
package com.palantir.baseline.classuniqueness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JarLabelsTest {
    private static final String MODULE = "foo";
    private static final String VERSION = "1.0";
    private static final String SNAPSHOT = "1.0-SNAPSHOT";

    @Test
    void no_classifier() {
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-1.0.jar")).isEmpty();
    }

    @Test
    void classifier() {
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-1.0-tests.jar")).hasValue("tests");
    }

    @Test
    void classifier_containing_dashes_and_dots() {
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-1.0-linux-x86_64.v2.jar"))
                .hasValue("linux-x86_64.v2");
    }

    @Test
    void module_name_ending_in_a_dash_and_digits() {
        assertThat(JarLabels.classifier("lib-2", "2", "lib-2-2.jar")).isEmpty();
        assertThat(JarLabels.classifier("lib-2", "2", "lib-2-2-tests.jar")).hasValue("tests");
    }

    @Test
    void classifier_containing_the_version() {
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-1.0-spark-1.0.jar"))
                .as("the version follows the module name, so a later occurrence is part of the classifier")
                .hasValue("spark-1.0");
    }

    @Test
    void unique_snapshot() {
        assertThat(JarLabels.classifier(MODULE, SNAPSHOT, "foo-1.0-20250101.123456-1.jar"))
                .isEmpty();
        assertThat(JarLabels.classifier(MODULE, SNAPSHOT, "foo-1.0-20250101.123456-12-tests.jar"))
                .hasValue("tests");
    }

    @Test
    void non_unique_snapshot() {
        assertThat(JarLabels.classifier(MODULE, SNAPSHOT, "foo-1.0-SNAPSHOT-tests.jar"))
                .hasValue("tests");
    }

    @Test
    void file_named_by_module_metadata_takes_the_classifier_after_the_last_version() {
        // Gradle Module Metadata can name a module's files anything; Gradle's own parse finds the version anywhere
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-core-1.0-natives.jar"))
                .hasValue("natives");
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-core-1.0.jar")).isEmpty();
    }

    @Test
    void file_name_without_the_version_has_no_classifier() {
        assertThat(JarLabels.classifier(MODULE, VERSION, "foo-tests.jar"))
                .as("Gradle reports no classifier when it can't find the version in a file name")
                .isEmpty();
    }
}
