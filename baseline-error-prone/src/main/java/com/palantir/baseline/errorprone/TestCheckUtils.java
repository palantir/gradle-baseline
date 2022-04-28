/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.JUnitMatchers;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

final class TestCheckUtils {

    private TestCheckUtils() {
        // utility class
    }

    /** Note that this is a relatively expensive check and should be executed after simpler validation. */
    static boolean isTestCode(VisitorState state) {
        if (state.errorProneOptions().isTestOnlyTarget()) {
            return true;
        }
        TreePath path = state.getPath();
        for (Tree ancestor : path) {
            if (ancestor instanceof ClassTree && hasTestCases.matches((ClassTree) ancestor, state)) {
                return true;
            }
        }
        return state.getPath().getCompilationUnit().getImports().stream()
                .map(ImportTree::getQualifiedIdentifier)
                .map(Object::toString)
                .anyMatch(TestCheckUtils::isTestImport);
    }

    private static final Matcher<ClassTree> hasJUnit5TestCases = Matchers.hasMethod(Matchers.anyOf(
            Matchers.hasAnnotationOnAnyOverriddenMethod("org.junit.jupiter.api.Test"),
            Matchers.hasAnnotationOnAnyOverriddenMethod("org.junit.jupiter.api.TestTemplate")));

    private static final Matcher<ClassTree> hasTestCases =
            Matchers.anyOf(JUnitMatchers.hasJUnit4TestCases, hasJUnit5TestCases);

    private static boolean isTestImport(String qualifiedName) {
        return qualifiedName.startsWith("org.junit.") // junit 4 and 5
                || qualifiedName.startsWith("junit.") // junit 3
                || qualifiedName.startsWith("org.mockito.")
                || qualifiedName.startsWith("org.assertj.");
    }
}
