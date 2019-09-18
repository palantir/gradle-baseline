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

package com.palantir.baseline.refaster;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class AssertjFileContent<T> {

    @BeforeTemplate
    void before(File file, String expected) throws IOException {
        assertThat(new String(Files.toByteArray(file), StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @BeforeTemplate
    void before2(File file, String expected) throws IOException {
        assertThat(Files.toString(file, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @AfterTemplate
    void after(File file, String expected) {
        assertThat(file).hasContent(expected);
    }
}
