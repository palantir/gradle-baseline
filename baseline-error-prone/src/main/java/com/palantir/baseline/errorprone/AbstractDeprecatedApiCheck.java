/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.util.Optional;
import javax.lang.model.element.Name;

/**
 * This is an abstract base class for checks meant to replace the `-Xlint:deprecation` and `-Xlint:removal` compiler
 *   flags.
 *
 * See {@link DeprecatedApiUsage} and {@link DeprecatedForRemovalApiUsage} for concrete implementations.
 */
public abstract class AbstractDeprecatedApiCheck extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher,
                BugChecker.MemberSelectTreeMatcher {

    protected abstract boolean isDeprecationWarning(Tree tree, VisitorState state);

    protected abstract String getErrorDescription(Optional<String> qualifiedName);

    @Override
    public final Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (!isDeprecationWarning(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<Symbol> symbol = Optional.ofNullable(ASTHelpers.getSymbol(tree));

        if (symbol.isPresent()) {
            Optional<Name> currentClass = getCurrentClass(state);
            if (currentClass.isPresent()
                    && currentClass.get().equals(symbol.get().owner.getQualifiedName())) {
                // Don't complain about deprecated APIs used within the same class
                return Description.NO_MATCH;
            }
        }

        Optional<String> qualifiedName = symbol.map(
                s -> s.owner.getQualifiedName() + "#" + s.getQualifiedName().toString());
        String description = getErrorDescription(qualifiedName);
        return buildDescription(tree).setMessage(description).build();
    }

    private Optional<Name> getCurrentClass(VisitorState state) {
        return Optional.ofNullable(ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class))
                .map(ASTHelpers::getSymbol)
                .map(Symbol::getQualifiedName);
    }
}
