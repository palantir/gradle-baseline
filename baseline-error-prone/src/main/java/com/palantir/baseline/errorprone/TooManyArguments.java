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
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.ProvidesFix;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        name = "TooManyArguments",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        providesFix = ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "Prefer Interface that take few arguments rather than many.")
public final class TooManyArguments extends BugChecker implements BugChecker.MethodTreeMatcher {
    private static final int MAX_NUM_ARGS = 10;
    private static final Matcher<Tree> IS_OVERRIDE = Matchers.hasAnnotation(Override.class);

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (IS_OVERRIDE.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null || symbol.isConstructor()) {
            return Description.NO_MATCH;
        }

        if (tree.getParameters().size() > MAX_NUM_ARGS) {
            return buildDescription(tree)
                    .setMessage("Interfaces can take at most " + MAX_NUM_ARGS
                            + " arguments. Consider the following ways of solving the problem:\n"
                            + "- Define an object with Immutables that contains all of the arguments\n"
                            + "- Expose smaller interfaces by refactorings concepts into separate interfaces")
                    .build();
        }

        return Description.NO_MATCH;
    }
}
