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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("DesignForExtension")
public abstract class SuppressibleBugChecker extends BugChecker {
    private static final String AUTOMATICALLY_ADDED_PREFIX = "suppressed-for-rollout:";

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

    private static Description match(Object bugChecker, Tree tree, VisitorState state, Description description) {
        if (description == Description.NO_MATCH) {
            return description;
        }

        boolean errorProneSuppressStage1 = state.errorProneOptions()
                .getFlags()
                .getBoolean("errorProneSuppressStage1")
                .orElse(false);

        if (!errorProneSuppressStage1) {
            return description;
        }

        Tree firstSuppressibleParent = Stream.iterate(state.getPath(), TreePath::getParentPath)
                .dropWhile(path -> !suppressibleKind(path.getLeaf().getKind()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find anything we can suppress"))
                .getLeaf();

        BugChecker bugChecker1 = (BugChecker) bugChecker;

        return bugChecker1
                .buildDescription(tree)
                .setMessage(description.getRawMessage())
                .setLinkUrl(description.getLink())
                .addFix(SuggestedFix.builder()
                        .prefixWith(
                                firstSuppressibleParent,
                                "@com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\""
                                        + AUTOMATICALLY_ADDED_PREFIX + bugChecker1.canonicalName() + "\")\n")
                        .build())
                .build();
    }

    // START GENERATED CODE
    interface MethodTreeMatcher extends BugChecker.MethodTreeMatcher {
        @Override
        default Description matchMethod(MethodTree tree, VisitorState state) {
            return match(this, tree, state, matchMethodSuppressible(tree, state));
        }

        Description matchMethodSuppressible(MethodTree tree, VisitorState state);
    }

    interface MethodInvocationTreeMatcher extends BugChecker.MethodInvocationTreeMatcher {
        /** @deprecated use {@link #matchMethodInvocationSuppressible} instead. */
        @Override
        @Deprecated
        default Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
            return match(this, tree, state, matchMethodInvocationSuppressible(tree, state));
        }

        Description matchMethodInvocationSuppressible(MethodInvocationTree tree, VisitorState state);
    }
    // END GENERATED CODE
}
