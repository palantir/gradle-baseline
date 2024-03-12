/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.bugpatterns.BugChecker;
import java.util.Set;
import java.util.function.Supplier;

abstract class BaselineBugChecker extends BugChecker {
    private static final String AUTOMATICALLY_ADDED_PREFIX = "automatically-added-for-baseline-upgrade:";

    private final Supplier<Set<String>> allNames = Suppliers.memoize(() -> {
        return ImmutableSet.<String>builder()
                .addAll(super.allNames())
                .add(AUTOMATICALLY_ADDED_PREFIX + super.canonicalName())
                .build();
    });

    @Override
    public Set<String> allNames() {
        return allNames.get();
    }
}
