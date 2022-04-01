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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnalysis;
import com.palantir.baseline.errorprone.safety.SafetyAnnotations;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.List;

/**
 * Ensures that safe-logging annotated elements are handled correctly by annotated method parameters.
 * Potential future work:
 * <ul>
 *     <li>We could check return statements in methods annotated for
 *     safety to require consistency</li>
 *     <li>Enforce propagation of safety annotations from fields and types to types which encapsulate them.</li>
 *     <li>More complex flow analysis to ensure safety information is respected.</li>
 * </ul>
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "safe-logging annotations must agree between args and method parameters")
public final class IllegalSafeLoggingArgument extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher,
                BugChecker.ReturnTreeMatcher,
                BugChecker.AssignmentTreeMatcher,
                BugChecker.CompoundAssignmentTreeMatcher {

    private static final String UNSAFE_ARG = "com.palantir.logsafe.UnsafeArg";
    private static final Matcher<ExpressionTree> SAFE_ARG_OF_METHOD_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.palantir.logsafe.SafeArg")
            .named("of");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<? extends ExpressionTree> arguments = tree.getArguments();
        if (arguments.isEmpty()) {
            return Description.NO_MATCH;
        }
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        if (methodSymbol == null) {
            return Description.NO_MATCH;
        }
        List<VarSymbol> parameters = methodSymbol.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VarSymbol parameter = parameters.get(i);
            Safety parameterSafety = SafetyAnnotations.getSafety(parameter, state);
            if (parameterSafety.allowsAll()) {
                // Fast path: all types are accepted, there's no reason to do further analysis.
                continue;
            }

            int limit = methodSymbol.isVarArgs() && i == parameters.size() - 1 ? arguments.size() : i + 1;
            for (int j = i; j < limit; j++) {
                ExpressionTree argument = arguments.get(j);

                Safety argumentSafety = SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), argument)));

                if (!parameterSafety.allowsValueWith(argumentSafety)) {
                    // use state.reportMatch to report all failing arguments if multiple are invalid
                    state.reportMatch(buildDescription(argument)
                            .setMessage(String.format(
                                    "Dangerous argument value: arg is '%s' but the parameter requires '%s'.",
                                    argumentSafety, parameterSafety))
                            .addFix(getSuggestedFix(tree, state, argumentSafety))
                            .build());
                }
            }
        }
        return Description.NO_MATCH;
    }

    private static SuggestedFix getSuggestedFix(MethodInvocationTree tree, VisitorState state, Safety argumentSafety) {
        if (SAFE_ARG_OF_METHOD_MATCHER.matches(tree, state) && Safety.UNSAFE.allowsValueWith(argumentSafety)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String unsafeQualifiedClassName = SuggestedFixes.qualifyType(state, fix, UNSAFE_ARG);
            String replacement = String.format("%s.of", unsafeQualifiedClassName);
            return fix.replace(tree.getMethodSelect(), replacement).build();
        }

        return SuggestedFix.emptyFix();
    }

    @Override
    public Description matchReturn(ReturnTree tree, VisitorState state) {
        if (tree.getExpression() == null) {
            return Description.NO_MATCH;
        }
        TreePath path = state.getPath();
        while (path != null && path.getLeaf() instanceof StatementTree) {
            path = path.getParentPath();
        }
        if (path == null || !(path.getLeaf() instanceof MethodTree)) {
            return Description.NO_MATCH;
        }
        MethodTree method = (MethodTree) path.getLeaf();
        Safety methodDeclaredSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(method), state);
        if (methodDeclaredSafety.allowsAll()) {
            // Fast path, all types are accepted, there's no reason to do further analysis.
            return Description.NO_MATCH;
        }
        Safety returnValueSafety =
                SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), tree.getExpression())));
        if (methodDeclaredSafety.allowsValueWith(returnValueSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Dangerous return value: result is '%s' but the method is annotated '%s'.",
                        returnValueSafety, methodDeclaredSafety))
                .build();
    }

    @Override
    public Description matchAssignment(AssignmentTree tree, VisitorState state) {
        return handleAssignment(tree, tree.getVariable(), tree.getExpression(), state);
    }

    @Override
    public Description matchCompoundAssignment(CompoundAssignmentTree tree, VisitorState state) {
        return handleAssignment(tree, tree.getVariable(), tree.getExpression(), state);
    }

    private Description handleAssignment(
            ExpressionTree assignmentTree, ExpressionTree variable, ExpressionTree expression, VisitorState state) {
        Safety variableDeclaredSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(variable), state);
        if (variableDeclaredSafety.allowsAll()) {
            return Description.NO_MATCH;
        }
        Safety assignmentValue = SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), expression)));
        if (variableDeclaredSafety.allowsValueWith(assignmentValue)) {
            return Description.NO_MATCH;
        }
        return buildDescription(assignmentTree)
                .setMessage(String.format(
                        "Dangerous assignment: value is '%s' but the variable is annotated '%s'.",
                        assignmentValue, variableDeclaredSafety))
                .build();
    }
}
