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
import com.google.common.collect.MoreCollectors;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

abstract class BaselineBugChecker extends BugChecker {
    private static final String AUTOMATICALLY_ADDED_PREFIX = "auto-added-on-upgrade:";

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

    interface BaselineMethodInvocationTreeMatcher<T extends BugChecker & BaselineMethodInvocationTreeMatcher<T>>
            extends BugChecker.MethodInvocationTreeMatcher {

        default BugChecker bugChecker() {
            return (BugChecker) this;
        }

        @Override
        default Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
            Description description = matchMethodInvocationBaseline(tree, state);

            if (description == Description.NO_MATCH) {
                return Description.NO_MATCH;
            }

            if (description.fixes.isEmpty()) {
                return description;
            }

            Tree firstSuppressibleParent = Stream.iterate(state.getPath(), TreePath::getParentPath)
                    .dropWhile(path -> !suppressibleKind(path.getLeaf().getKind()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Can't find anything we can suppress"))
                    .getLeaf();

            MethodTree methodTree = (MethodTree) firstSuppressibleParent;
            AnnotationTree suppressWarnings = methodTree.getModifiers().getAnnotations().stream()
                    .filter(annotationTree -> ((IdentifierTree) annotationTree.getAnnotationType())
                            .getName()
                            .contentEquals("SuppressWarnings"))
                    .collect(MoreCollectors.onlyElement());

            return bugChecker()
                    .buildDescription(tree)
                    .setMessage(description.getRawMessage())
                    .setLinkUrl(description.getLink())
                    .addFix(SuggestedFix.prefixWith(
                            suppressWarnings,
                            "// SuppressWarning(" + AUTOMATICALLY_ADDED_PREFIX + canonicalName() + Math.random()
                                    + ")\n"))
                    .build();
        }

        Description matchMethodInvocationBaseline(MethodInvocationTree tree, VisitorState state);
    }

    private static boolean suppressibleKind(Tree.Kind kind) {
        switch (kind) {
            case CLASS:
            case METHOD:
            case VARIABLE:
                // What about fields?
                return true;
            default:
                return false;
        }
    }
}
