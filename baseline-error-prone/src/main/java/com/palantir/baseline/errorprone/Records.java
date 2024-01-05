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

import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.List;

public final class Records {

    @SuppressWarnings("unchecked")
    public static List<VarSymbol> getRecordComponents(ClassSymbol classSymbol) {
        if (!ASTHelpers.isRecord(classSymbol)) {
            throw new RuntimeException("Expected a record, was: " + classSymbol);
        }
        // Can use classSymbol.getRecordComponents() in future versions
        try {
            return (List<VarSymbol>)
                    ClassSymbol.class.getMethod("getRecordComponents").invoke(classSymbol);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get record components", e);
        }
    }

    private Records() {}
}
