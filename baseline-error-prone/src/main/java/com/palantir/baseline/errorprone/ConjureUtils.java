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
import com.sun.tools.javac.code.Type;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

final class ConjureUtils {

    static boolean isConjureUnion(Type type) {
        // TODO(kkak): Update this to check for the presence of @ConjureGenerated
        if (type.asElement() == null
                || type.asElement().getKind() != ElementKind.CLASS
                || !type.asElement().getModifiers().contains(Modifier.SEALED)) {
            return false;
        }

        // Check if it has a nested interface called "Known"
        return ASTHelpers.getEnclosedElements(type.asElement()).stream()
                .anyMatch(element -> element.getKind() == ElementKind.INTERFACE
                        && element.getModifiers().contains(Modifier.SEALED)
                        && "Known".equals(element.getSimpleName().toString()));
    }

    private ConjureUtils() {}
}
