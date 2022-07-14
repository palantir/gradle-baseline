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
import com.sun.source.tree.*;

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

        SuggestedFix.Builder fix = SuggestedFix.builder();
        StringBuilder replacementBuilder = new StringBuilder();

        String methodInvocation = state.getSourceForNode(tree.getMethodSelect());
        String receiverOnly = methodInvocation.replaceAll("\\.accept", "");
        replacementBuilder.append(String.format("switch (%s) {", receiverOnly));

        String unionName;
        List<UnionCase> unionCases;

        if (onlyArgument instanceof MethodInvocationTree methodTree) {
            String[] parts = state.getSourceForNode(methodTree).split("\\.");
            if (parts.length == 0) {
                return Description.NO_MATCH;
            }
            unionName = parts[0];
            unionCases = getUnionCases(state, methodTree);
        } else if (onlyArgument instanceof NewClassTree newClass) {
            String[] parts = state.getSourceForNode(newClass.getIdentifier()).split("\\.");
            if (parts.length == 0) {
                return Description.NO_MATCH;
            }
            unionName = parts[0];
            unionCases = getUnionCasesFromClass(state, newClass);
        } else {
            return Description.NO_MATCH;
        }

        if (unionCases.isEmpty()) {
            return Description.NO_MATCH;
        }
        for (UnionCase unionCase : unionCases) {
            String caseStatement = String.format(
                    "case %s.%s %s -> %s",
                    unionName, unionCase.getCaseName(), unionCase.getVariableName(), unionCase.getStatement());
            replacementBuilder.append(caseStatement);
            if (!caseStatement.endsWith("}")) {
                replacementBuilder.append(";");
            }
        }

        replacementBuilder.append("}");
        fix.replace(tree, replacementBuilder.toString());

        return buildDescription(tree)
                .setMessage("Prefer Java 17 switch")
                .addFix(fix.build())
                .build();
    }

    private static List<UnionCase> getUnionCasesFromClass(VisitorState state, NewClassTree newClass) {
        List<UnionCase> unionCases = new ArrayList<>();

        for (Tree newClassTree : newClass.getClassBody().getMembers()) {
            if (newClassTree instanceof MethodTree methodDeclaration) {
                if (methodDeclaration.getName().toString().startsWith("visit")) {
                    String caseName = methodDeclaration.getName().toString().substring(5);
                    String statement = state.getSourceForNode(methodDeclaration.getBody());

                    if (caseName.equals("Unknown")) {
                        if (methodDeclaration.getParameters().size() == 0) {
                            return List.of();
                        }
                        String variableName = methodDeclaration
                                .getParameters()
                                .get(0)
                                .getName()
                                .toString();

                        unionCases.add(
                                new UnionCase(caseName, variableName, transformUnknown(statement, variableName)));
                    } else {
                        if (methodDeclaration.getParameters().size() != 1) {
                            return List.of();
                        }
                        String variableName = Iterables.getOnlyElement(methodDeclaration.getParameters())
                                .getName()
                                .toString();

                        unionCases.add(new UnionCase(caseName, variableName, transformCase(statement, variableName)));
                    }
                }
            }
        }

        return unionCases;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
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
                            String caseName =
                                    uppercase(memberSelect.getIdentifier().toString());
                            String variableName =
                                    state.getSourceForNode(Iterables.getOnlyElement(lambda.getParameters()));
                            String statement = state.getSourceForNode(lambda.getBody());

                            cases.add(new UnionCase(
                                    caseName,
                                    variableName,
                                    caseName.equals("Unknown")
                                            ? transformUnknown(statement, variableName)
                                            : transformCase(statement, variableName)));
                        } else {
                            return List.of();
                        }
                    } else if (argument instanceof MemberReferenceTree memberReference) {
                        String caseName = uppercase(memberSelect.getIdentifier().toString());
                        String variableName = memberSelect.getIdentifier().toString();
                        String statement = String.format(
                                "%s.%s(%s)",
                                state.getSourceForNode(memberReference.getQualifierExpression()),
                                memberReference.getName().toString(),
                                memberSelect.getIdentifier().toString());

                        cases.add(new UnionCase(
                                caseName,
                                variableName,
                                caseName.equals("Unknown")
                                        ? transformUnknown(statement, variableName)
                                        : transformCase(statement, variableName)));
                    } else if (argument instanceof MethodInvocationTree methodInvocation) {
                        String method = state.getSourceForNode(methodInvocation.getMethodSelect());
                        if (method.equals("Function.identity")) {
                            String caseName =
                                    uppercase(memberSelect.getIdentifier().toString());
                            String variableName = memberSelect.getIdentifier().toString();
                            String statement = memberSelect.getIdentifier().toString();

                            cases.add(new UnionCase(
                                    caseName,
                                    variableName,
                                    caseName.equals("Unknown")
                                            ? transformUnknown(statement, variableName)
                                            : transformCase(statement, variableName)));
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

    private static String transformCase(String statement, String variableName) {
        // This is shit.
        return statement.replaceAll(variableName, variableName + ".value()").replaceAll("return", "yield");
    }

    private static String transformUnknown(String statement, String variableName) {
        // This is shit.
        return statement.replaceAll(variableName, variableName + ".getType()").replaceAll("return", "yield");
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
