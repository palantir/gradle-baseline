/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class GuavaPreconditionsConstantMessageTests extends PreconditionsTests {

    private static final String DIAGNOSTIC = "non-constant message";

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PreconditionsConstantMessage.class, getClass());
    }

    @Override
    public CompilationTestHelper compilationHelper() {
        return compilationHelper;
    }

    @Test
    public void testCheckArgument_stringConcatenate() {
        failGuava(DIAGNOSTIC, "Preconditions.checkArgument(param != \"string\", \"constant\" + param);");
    }

    @Test
    public void testCheckArgument_stringFormat() {
        failGuava(DIAGNOSTIC,
                "Preconditions.checkArgument(param != \"string\", String.format(\"constant %s\", param));");
    }

    @Test
    public void testCheckState_stringConcatenate() {
        failGuava(DIAGNOSTIC, "Preconditions.checkState(param != \"string\", \"constant\" + param);");
    }

    @Test
    public void testCheckState_stringFormat() {
        failGuava(DIAGNOSTIC,
                "Preconditions.checkState(param != \"string\", String.format(\"constant %s\", param));");
    }

    @Test
    public void testCheckNotNull_stringConcatenate() {
        failGuava(DIAGNOSTIC, "Preconditions.checkNotNull(param, \"constant\" + param);");
    }

    @Test
    public void testCheckNotNull_stringFormat() {
        failGuava(DIAGNOSTIC, "Preconditions.checkNotNull(param, String.format(\"constant %s\", param));");
    }
}
