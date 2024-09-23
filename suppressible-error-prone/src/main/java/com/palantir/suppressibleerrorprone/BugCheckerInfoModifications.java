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

package com.palantir.suppressibleerrorprone;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugCheckerInfo;
import java.lang.reflect.Field;

public final class BugCheckerInfoModifications {
    public static void addAutomaticallyAddedPrefix(BugCheckerInfo bugCheckerInfo) {
        try {
            // Preferring to use reflection to reduce bytecode editing (even though bytecode editing may result
            // in a "cleaner" result) as if the field name changes, the error should be much clearer here than if
            // the bytecode version goes wrong.
            Field allNamesField = BugCheckerInfo.class.getDeclaredField("allNames");
            allNamesField.setAccessible(true);
            ImmutableSet<String> currentAllNames = (ImmutableSet<String>) allNamesField.get(bugCheckerInfo);
            allNamesField.set(
                    bugCheckerInfo,
                    ImmutableSet.<String>builder()
                            .addAll(currentAllNames)
                            .add(CommonConstants.AUTOMATICALLY_ADDED_PREFIX + bugCheckerInfo.canonicalName())
                            .build());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "supressible-error-prone failed to modidy the allNames field of a BugChecker", e);
        }
    }

    private BugCheckerInfoModifications() {}
}
