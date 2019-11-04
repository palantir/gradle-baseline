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

import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.Tree;

/** Additional {@link Matcher} factory methods shared by baseline checks. */
final class MoreMatchers {

    /**
     * Delegates to {@link Matchers#isSubtypeOf(Class)}, but adds a defensive check against null literals
     * to work around error-prone#1397.
     *
     * @see Matchers#isSubtypeOf(Class)
     * @see <a href="https://github.com/google/error-prone/issues/1397">error-prone#1397</a>
     */
    static <T extends Tree> Matcher<T> isSubtypeOf(Class<?> baseType) {
        return Matchers.allOf(
                Matchers.isSubtypeOf(baseType),
                Matchers.not(Matchers.kindIs(Tree.Kind.NULL_LITERAL)));
    }

    /**
     * Delegates to {@link Matchers#isSubtypeOf(String)}, but adds a defensive check against null literals
     * to work around error-prone#1397.
     *
     * @see Matchers#isSubtypeOf(String)
     * @see <a href="https://github.com/google/error-prone/issues/1397">error-prone#1397</a>
     */
    static <T extends Tree> Matcher<T> isSubtypeOf(String baseTypeString) {
        return Matchers.allOf(
                Matchers.isSubtypeOf(baseTypeString),
                Matchers.not(Matchers.kindIs(Tree.Kind.NULL_LITERAL)));
    }

    private MoreMatchers() {}
}
