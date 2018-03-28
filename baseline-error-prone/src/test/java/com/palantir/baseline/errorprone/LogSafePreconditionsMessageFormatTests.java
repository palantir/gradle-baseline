/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public final class LogSafePreconditionsMessageFormatTests extends PreconditionsTests {
    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(LogSafePreconditionsMessageFormat.class, getClass());
    }

    @Override
    public CompilationTestHelper compilationHelper() {
        return compilationHelper;
    }

    @Test
    public void positive() throws Exception {
        String diagnostic = "Use '{}' style formatting";
        failLogSafe(diagnostic, "Preconditions.checkArgument(param != \"string\", \"message %s\","
                + " UnsafeArg.of(\"long\", 123L));");
        failLogSafe(diagnostic, "Preconditions.checkState(param != \"string\", \"message %s\","
                + " UnsafeArg.of(\"long\", 123L));");
        failLogSafe(diagnostic, "Preconditions.checkNotNull(param, \"message %s\","
                + " UnsafeArg.of(\"long\", 123L));");

        failLogSafe(diagnostic, "Preconditions.checkArgument(param != \"string\", \"message %s %s\","
                + " UnsafeArg.of(\"char1\", 'a'), UnsafeArg.of(\"char2\", 'b'));");
        failLogSafe(diagnostic, "Preconditions.checkState(param != \"string\", \"message %s %s\","
                + " UnsafeArg.of(\"char1\", 'a'), UnsafeArg.of(\"char2\", 'b'));");
        failLogSafe(diagnostic, "Preconditions.checkNotNull(param, \"message %s %s\","
                + " UnsafeArg.of(\"char1\", 'a'), UnsafeArg.of(\"char2\", 'b'));");
    }
}
