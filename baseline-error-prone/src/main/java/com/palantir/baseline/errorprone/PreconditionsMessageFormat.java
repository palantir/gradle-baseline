/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;

abstract class PreconditionsMessageFormat extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private final Matcher<ExpressionTree> methodMatcher;

    protected PreconditionsMessageFormat(Matcher<ExpressionTree> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    protected abstract Description matchMessageFormat(MethodInvocationTree tree, String message, VisitorState state);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!methodMatcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (args.size() <= 1) {
            return Description.NO_MATCH;
        }

        ExpressionTree messageArg = args.get(1);
        if (!messageArg.getKind().equals(Tree.Kind.STRING_LITERAL)) {
            return Description.NO_MATCH;
        }

        String message;
        try {
            message = (String) ((LiteralTree) messageArg).getValue();
        } catch (ClassCastException exception) {
            return Description.NO_MATCH;
        }

        return matchMessageFormat(tree, message, state);
    }
}
