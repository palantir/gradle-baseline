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

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Locale;
import javax.lang.model.element.Modifier;

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

    /**
     * Matches enclosing classes on {@link ClassTree} blocks. This differs from
     * {@link Matchers#enclosingClass(Matcher)} which matches the input {@link ClassTree},
     * not the enclosing class.
     */
    static <T extends ClassTree> Matcher<T> classEnclosingClass(Matcher<ClassTree> matcher) {
        return (Matcher<T>) (classTree, state) -> {
            TreePath currentPath = state.getPath().getParentPath();
            while (currentPath != null) {
                Tree leaf = currentPath.getLeaf();
                if (leaf instanceof ClassTree) {
                    return matcher.matches((ClassTree) leaf, state);
                }
                currentPath = currentPath.getParentPath();
            }
            return false;
        };
    }

    /**
     * Works similarly to {@link Matchers#hasModifier(Modifier)}, but only matches elements
     * which explicitly list the modifier. For example, all components nested in an interface
     * are public by default, but they don't necessarily use the public keyword.
     */
    static <T extends Tree> Matcher<T> hasExplicitModifier(Modifier modifier) {
        return (Matcher<T>) (tree, state) -> {
            if (tree instanceof ClassTree) {
                return containsModifier(((ClassTree) tree).getModifiers(), state, modifier);
            }
            if (tree instanceof MethodTree) {
                return containsModifier(((MethodTree) tree).getModifiers(), state, modifier);
            }
            if (tree instanceof VariableTree) {
                return containsModifier(((VariableTree) tree).getModifiers(), state, modifier);
            }
            return false;
        };
    }

    private static boolean containsModifier(ModifiersTree tree, VisitorState state, Modifier modifier) {
        if (!tree.getFlags().contains(modifier)) {
            return false;
        }
        String source = state.getSourceForNode(tree);
        // getSourceForNode returns null when there are no modifiers specified
        if (source == null) {
            return false;
        }
        // nested interfaces report a static modifier despite not being present
        return source.contains(modifier.name().toLowerCase(Locale.ENGLISH));
    }

    private MoreMatchers() {}
}
