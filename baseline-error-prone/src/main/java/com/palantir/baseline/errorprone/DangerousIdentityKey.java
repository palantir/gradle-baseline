/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.AbstractAsKeyOfSetOrMap;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Name;

/**
 * Warns that users should not have a {@link java.util.regex.Pattern} as a key to a Set or Map.
 */
@BugPattern(
        name = "DangerousIdentityKey",
        summary = "Key type does not override equals() and hashCode, so comparisons will be done on"
                + " reference equality only. If neither deduplication nor lookup are needed,"
                + " consider using a List instead. Otherwise, use IdentityHashMap/Set,"
                + " or an Iterable/List of pairs.",
        severity = SeverityLevel.WARNING)
public final class DangerousIdentityKey extends AbstractAsKeyOfSetOrMap {

    @Override
    protected boolean isBadType(Type type, VisitorState state) {
        // Only flag final types, otherwise we'll encounter false positives when presented with overrides.
        if (type == null || !type.isFinal()) {
            return false;
        }
        return !implementsMethod(state.getTypes(), type, state.getNames().equals, state)
                || !implementsMethod(state.getTypes(), type, state.getNames().hashCode, state);
    }

    private static boolean implementsMethod(Types types, Type type, Name methodName, VisitorState state) {
        MethodSymbol equals =
                (MethodSymbol) state.getSymtab().objectType.tsym.members().findFirst(methodName);
        return !Iterables.isEmpty(types.membersClosure(type, false)
                .getSymbolsByName(methodName, m -> m != equals && m.overrides(equals, type.tsym, types, false)));
    }
}
