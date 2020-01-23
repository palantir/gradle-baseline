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
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LoggerEnclosingClass",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Loggers created using getLogger(Class<?>) must reference their enclosing class.")
public final class LoggerEnclosingClass extends BugChecker implements BugChecker.VariableTreeMatcher {

    private static final Matcher<VariableTree> matcher = Matchers.allOf(
            Matchers.isField(),
            Matchers.isSubtypeOf("org.slf4j.Logger"),
            Matchers.variableInitializer(MethodMatchers.staticMethod()
                    .onClass("org.slf4j.LoggerFactory")
                    .named("getLogger")
                    // Only match the 'class' constructor
                    .withParameters(Class.class.getName())));

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (!matcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        MethodInvocationTree getLoggerInvocation = (MethodInvocationTree) tree.getInitializer();
        ExpressionTree classArgument = getLoggerInvocation.getArguments().get(0);

        if (!(classArgument instanceof MemberSelectTree)) {
            return Description.NO_MATCH;
        }
        MemberSelectTree memberSelectTree = (MemberSelectTree) classArgument;
        if (!memberSelectTree.getIdentifier().contentEquals("class")
                || memberSelectTree.getExpression().getKind() != Tree.Kind.IDENTIFIER) {
            return Description.NO_MATCH;
        }

        Symbol targetSymbol = ASTHelpers.getSymbol(memberSelectTree.getExpression());
        Symbol.ClassSymbol enclosingClassSymbol =
                enclosingConcreteClass(ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree)));
        if (targetSymbol == null || enclosingClassSymbol == null) {
            return Description.NO_MATCH;
        }
        if (state.getTypes().isSameType(targetSymbol.type, enclosingClassSymbol.type)) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        return buildDescription(classArgument)
                .addFix(fix.replace(classArgument, enclosingClassSymbol.name + ".class")
                        .build())
                .build();
    }

    private static Symbol.ClassSymbol enclosingConcreteClass(Symbol.ClassSymbol input) {
        Symbol.ClassSymbol current = input;
        while (current != null && current.isAnonymous()) {
            current = ASTHelpers.enclosingClass(current);
        }
        return current;
    }
}
