/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type.ClassType;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.DeclaredType;

public final class SymbolPredicates {
    private SymbolPredicates() {}

    public static Predicate<Symbol> hasAnnotationWithPackage(String packagePrefix) {
        return symbol -> {
            for (AnnotationMirror mirror : symbol.getAnnotationMirrors()) {
                DeclaredType annotationType = mirror.getAnnotationType();
                if (annotationType instanceof ClassType
                        && annotationType.toString().startsWith(packagePrefix)) {
                    return true;
                }
            }

            return false;
        };
    }
}
