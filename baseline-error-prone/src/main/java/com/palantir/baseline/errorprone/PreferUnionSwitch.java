/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.List;

/**
 * TODOs:
 * - don't rewrite _all_ visitors, only those that are a sealed interface
 * - visitor builders
 * - anonymously defined visitor classes
 *
 * Things we probably can't solve:
 * - if people have extended
 * - functions returning visitors (e.g. all the utilities in Apollo) <- could lint against this?
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Prefer switch expressions instead of manually constructed visitors for Conjure unions")
public final class PreferUnionSwitch extends BugChecker implements MethodInvocationTreeMatcher {
    private static final Matcher<ExpressionTree> ACCEPT_METHOD_MATCHER = Matchers.instanceMethod()
            // only care about unions with the new 'sealed interface codegen'
            .onClass((type, _state) -> {
                // TODO(dfox): do we need to filter down to conjure-generated unions?
                return type.isInterface();
            })
            // .anyClass()
            .named("accept");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!ACCEPT_METHOD_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> arguments = tree.getArguments();
        if (arguments.size() != 1) {
            return Description.NO_MATCH;
        }
        ExpressionTree onlyArgument = Iterables.getOnlyElement(arguments);
        if (!(onlyArgument instanceof MethodInvocationTree)) {
            return Description.NO_MATCH;
        }
        MethodInvocationTree visitorBuilder = (MethodInvocationTree) onlyArgument;

        String[] parts = state.getSourceForNode(visitorBuilder).split("\\.");
        if (parts.length == 0) {
            return Description.NO_MATCH;
        }
        String unionName = parts[0];

        List<UnionCase> unionCases = getUnionCases(state, visitorBuilder);
        if (unionCases.isEmpty()) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fix = SuggestedFix.builder();
        StringBuilder replacementBuilder = new StringBuilder();

        String methodInvocation = state.getSourceForNode(tree.getMethodSelect());
        String receiverOnly = methodInvocation.replaceAll("\\.accept", "");
        replacementBuilder.append(String.format("switch (%s) {", receiverOnly));

        for (UnionCase unionCase : unionCases) {
            replacementBuilder.append(String.format(
                    "case %s.%s %s -> %s;",
                    unionName,
                    unionCase.getCaseName(),
                    unionCase.getVariableName(),
                    unionCase
                            .getStatement()
                            // This is shit.
                            .replaceAll(unionCase.getVariableName(), unionCase.getVariableName() + ".value()")));
        }

        replacementBuilder.append("}");
        fix.replace(tree, replacementBuilder.toString());

        return buildDescription(tree)
                .setMessage("Prefer Java 17 switch")
                .addFix(fix.build())
                .build();
    }

    private static List<UnionCase> getUnionCases(VisitorState state, MethodInvocationTree visitorBuilder) {
        MethodInvocationTree tree = visitorBuilder;
        List<UnionCase> cases = new ArrayList<>();
        while (true) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree memberSelect)) {
                return List.of();
            }

            if (memberSelect.getIdentifier().toString().equals("builder")) {
                break;
            }

            if (!memberSelect.getIdentifier().toString().equals("build")) {
                if (tree.getArguments().size() == 1) {
                    ExpressionTree argument = Iterables.getOnlyElement(tree.getArguments());
                    if (argument instanceof LambdaExpressionTree lambda) {
                        if (lambda.getParameters().size() == 1) {
                            VariableTree variable = Iterables.getOnlyElement(lambda.getParameters());
                            Tree statement = lambda.getBody();

                            cases.add(new UnionCase(
                                    uppercase(memberSelect.getIdentifier().toString()),
                                    state.getSourceForNode(variable),
                                    state.getSourceForNode(statement)));
                        } else {
                            return List.of();
                        }
                    } else if (argument instanceof MemberReferenceTree memberReference) {
                        String statement = String.format(
                                "%s.%s(%s)",
                                state.getSourceForNode(memberReference.getQualifierExpression()),
                                memberReference.getName().toString(),
                                memberSelect.getIdentifier().toString());

                        cases.add(new UnionCase(
                                uppercase(memberSelect.getIdentifier().toString()),
                                memberSelect.getIdentifier().toString(),
                                statement));
                    } else if (argument instanceof MethodInvocationTree methodInvocation) {
                        String method = state.getSourceForNode(methodInvocation.getMethodSelect());
                        if (method.equals("Function.identity")) {
                            cases.add(new UnionCase(
                                    uppercase(memberSelect.getIdentifier().toString()),
                                    memberSelect.getIdentifier().toString(),
                                    memberSelect.getIdentifier().toString()));
                        } else {
                            return List.of();
                        }
                    } else {
                        return List.of();
                    }
                } else {
                    return List.of();
                }
            }

            if (memberSelect.getExpression() instanceof MethodInvocationTree nextMethod) {
                tree = nextMethod;
            } else {
                return List.of();
            }
        }
        return ImmutableList.copyOf(Lists.reverse(cases));
    }

    private static String uppercase(String name) {
        String cleanName = name.endsWith("_") ? name.substring(0, name.length() - 1) : name;
        return cleanName.substring(0, 1).toUpperCase() + cleanName.substring(1);
    }

    private static class UnionCase {
        private final String caseName;
        private final String variableName;
        private final String statement;

        UnionCase(String caseName, String variableName, String statement) {
            this.caseName = caseName;
            this.variableName = variableName;
            this.statement = statement;
        }

        public String getCaseName() {
            return caseName;
        }

        public String getVariableName() {
            return variableName;
        }

        public String getStatement() {
            return statement;
        }
    }
}
