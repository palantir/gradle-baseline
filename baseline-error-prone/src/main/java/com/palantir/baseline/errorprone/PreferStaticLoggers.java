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

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferStaticLoggers",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Prefer static logger instances over instances to reduce object initialization costs and heap "
                + "overhead. Some logger frameworks may run expensive classloader lookups when loggers are requested "
                + "based on configuration.")
public final class PreferStaticLoggers extends BugChecker implements BugChecker.VariableTreeMatcher {

    private static final Matcher<Tree> IS_LOGGER = Matchers.isSubtypeOf("org.slf4j.Logger");
    private static final Matcher<ExpressionTree> LOGGER_FACTORY = MethodMatchers.staticMethod()
            .onClass("org.slf4j.LoggerFactory")
            .named("getLogger")
            .withParameters("java.lang.Class");

    private static final Matcher<ExpressionTree> GET_CLASS =
            MethodMatchers.instanceMethod().anyClass().named("getClass").withParameters();

    private static final Matcher<VariableTree> IS_FIELD = Matchers.isField();

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        // Only applies to loggers with initializers, to avoid impacting
        // classes which may take a logger as an argument.
        ExpressionTree initializer = tree.getInitializer();
        if (initializer == null) {
            return Description.NO_MATCH;
        }
        if (!IS_FIELD.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        if (!IS_LOGGER.matches(tree.getType(), state)) {
            return Description.NO_MATCH;
        }
        VarSymbol symbol = ASTHelpers.getSymbol(tree);
        if (symbol.isStatic()) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        SuggestedFixes.addModifiers(tree, state, Modifier.STATIC).ifPresent(fix::merge);
        if (LOGGER_FACTORY.matches(initializer, state)) {
            MethodInvocationTree invocation = (MethodInvocationTree) initializer;
            ExpressionTree argument = invocation.getArguments().get(0);
            if (ASTHelpers.getReceiver(argument) == null && GET_CLASS.matches(argument, state)) {
                fix.replace(
                        argument, SuggestedFixes.qualifyType(state, fix, ASTHelpers.enclosingClass(symbol) + ".class"));
            }
        }
        return buildDescription(tree).addFix(fix.build()).build();
    }
}
