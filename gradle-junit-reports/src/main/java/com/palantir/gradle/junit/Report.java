/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.junit;

import java.util.List;
import javax.annotation.Nullable;
import org.inferred.freebuilder.FreeBuilder;

@FreeBuilder
interface Report {

    @FreeBuilder
    public interface Failure {
        String message();
        String details();

        Builder toBuilder();
        class Builder extends Report_Failure_Builder { }
    }

    @FreeBuilder
    public interface TestCase {
        String name();
        @Nullable Failure failure();

        Builder toBuilder();
        class Builder extends Report_TestCase_Builder { }
    }

    String name();
    String subname();
    long elapsedTimeNanos();
    List<TestCase> testCases();

    Builder toBuilder();
    class Builder extends Report_Builder { }
}
