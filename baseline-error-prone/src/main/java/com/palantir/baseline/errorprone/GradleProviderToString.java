/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.AbstractToString;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.predicates.TypePredicate;
import com.google.errorprone.predicates.TypePredicates;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "GradleProviderToString",
        summary = "Calling toString on a Provider does not render the contained value",
        severity = BugPattern.SeverityLevel.ERROR)
public final class GradleProviderToString extends AbstractToString {

    private static final TypePredicate IS_PROVIDER = TypePredicates.isDescendantOf("org.gradle.api.provider.Provider");

    @Override
    protected TypePredicate typePredicate() {
        return IS_PROVIDER;
    }

    @Override
    protected Optional<Fix> implicitToStringFix(ExpressionTree tree, VisitorState state) {
        // Note that this might not always be the right thing to do, but it's right in enough cases we should do it.
        return Optional.of(SuggestedFix.postfixWith(tree, ".get()"));
    }

    @Override
    protected Optional<Fix> toStringFix(Tree parent, ExpressionTree expression, VisitorState state) {
        return Optional.empty();
    }
}
