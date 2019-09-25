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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class GuavaPreconditionsMessageFormatTests extends PreconditionsTests {

    private static final String DIAGNOSTIC = "Use printf-style formatting";

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(GuavaPreconditionsMessageFormat.class, getClass());
    }

    @Override
    public CompilationTestHelper compilationHelper() {
        return compilationHelper;
    }

    @Test
    public void testCheckArgument() {
        failGuava(DIAGNOSTIC, "Preconditions.checkArgument(param != \"string\", \"message {}\", 123L);");
    }

    @Test
    public void testCheckArgument_multipleArgs() {
        failGuava(DIAGNOSTIC, "Preconditions.checkArgument(param != \"string\", \"message {} {}\", 'a', 'b');");
    }

    @Test
    public void testCheckState() {
        failGuava(DIAGNOSTIC, "Preconditions.checkState(param != \"string\", \"message {}\", 123L);");
    }

    @Test
    public void testCheckState_multipleArgs() {
        failGuava(DIAGNOSTIC, "Preconditions.checkState(param != \"string\", \"message {} {}\", 'a', 'b');");
    }

    @Test
    public void testCheckNotNull() {
        failGuava(DIAGNOSTIC, "Preconditions.checkNotNull(param, \"message {}\", 123L);");
    }

    @Test
    public void testCheckNotNull_multipleArgs() {
        failGuava(DIAGNOSTIC, "Preconditions.checkNotNull(param, \"message {} {}\", 'a', 'b');");
    }
}
