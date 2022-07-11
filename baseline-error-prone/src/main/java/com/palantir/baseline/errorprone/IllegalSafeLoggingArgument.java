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
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import java.util.List;
import java.util.Objects;

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
                BugChecker.CompoundAssignmentTreeMatcher,
                BugChecker.MethodTreeMatcher,
                BugChecker.VariableTreeMatcher,
                BugChecker.NewClassTreeMatcher,
                BugChecker.ClassTreeMatcher {

    private static final String UNSAFE_ARG = "com.palantir.logsafe.UnsafeArg";
    private static final Matcher<ExpressionTree> SAFE_ARG_OF_METHOD_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.palantir.logsafe.SafeArg")
            .named("of");

    private static Type resolveParameterType(Type input, ExpressionTree tree, VisitorState state) {
        // Important not to call getReceiver/getReceiverType on a NewClassTree, which throws.
        if (input instanceof TypeVar && tree instanceof MethodInvocationTree) {
            TypeVar typeVar = (TypeVar) input;

            Type receiver = ASTHelpers.getReceiverType(tree);
            if (receiver == null) {
                return input;
            }
            Symbol symbol = ASTHelpers.getSymbol(tree);
            // List<String> -> Collection<E> gives us Collection<String>
            Type boundToMethodOwner = state.getTypes().asSuper(receiver, symbol.owner);
            List<TypeVariableSymbol> ownerTypeVars = symbol.owner.getTypeParameters();
            // Validate that the type parameters match -- it's possible raw types are used, and
            // no type variables are bound. See IllegalSafeLoggingArgumentTest.testRawTypes.
            if (ownerTypeVars.size() == boundToMethodOwner.getTypeArguments().size()) {
                for (int i = 0; i < ownerTypeVars.size(); i++) {
                    TypeVariableSymbol ownerVar = ownerTypeVars.get(i);
                    if (Objects.equals(ownerVar, typeVar.tsym)) {
                        return boundToMethodOwner.getTypeArguments().get(i);
                    }
                }
            }
        }
        return input;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return matchCtorOrMethodInvocation(
                tree, tree.getTypeArguments(), tree.getArguments(), ASTHelpers.getSymbol(tree), state);
    }

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        return matchCtorOrMethodInvocation(
                tree, tree.getTypeArguments(), tree.getArguments(), ASTHelpers.getSymbol(tree), state);
    }

    @SuppressWarnings({"CheckStyle", "ReferenceEquality"})
    private Description matchCtorOrMethodInvocation(
            ExpressionTree tree,
            List<? extends Tree> typeArguments,
            List<? extends ExpressionTree> arguments,
            MethodSymbol methodSymbol,
            VisitorState state) {
        if (methodSymbol == null) {
            return Description.NO_MATCH;
        }
        handleResultTypeArguments(tree, state);
        handleMethodTypeArguments(tree, typeArguments, methodSymbol, state);
        if (arguments.isEmpty()) {
            return Description.NO_MATCH;
        }
        List<VarSymbol> parameters = methodSymbol.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VarSymbol parameter = parameters.get(i);
            Type resolvedParameterType = resolveParameterType(parameter.type, tree, state);
            Safety parameterSafety = Safety.mergeAssumingUnknownIsSame(
                    SafetyAnnotations.getSafety(parameter, state),
                    SafetyAnnotations.getSafety(resolvedParameterType, state),
                    SafetyAnnotations.getSafety(resolvedParameterType.tsym, state));
            // Collect additional safety info from the declared type
            // Reference equality is okay because 'resolveParameterType' returns the input if the type doesn't need to
            // be resolved.
            if (parameter.type != resolvedParameterType) {
                parameterSafety = Safety.mergeAssumingUnknownIsSame(
                        parameterSafety,
                        SafetyAnnotations.getSafety(parameter.type, state),
                        SafetyAnnotations.getSafety(parameter.type.tsym, state));
            }
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

    private void handleResultTypeArguments(ExpressionTree tree, VisitorState state) {
        Type type = ASTHelpers.getResultType(tree);
        if (type != null && !type.getTypeArguments().isEmpty()) {
            List<Type> resultTypeArguments = type.getTypeArguments();
            List<TypeVariableSymbol> parameterTypes = type.tsym.getTypeParameters();
            if (parameterTypes.size() == resultTypeArguments.size()) {
                for (int i = 0; i < parameterTypes.size(); i++) {
                    TypeVariableSymbol typeVar = parameterTypes.get(i);
                    Type typeArgumentType = resultTypeArguments.get(i);
                    Safety typeVarSafety = Safety.mergeAssumingUnknownIsSame(
                            SafetyAnnotations.getSafety(typeVar, state),
                            SafetyAnnotations.getSafety(typeVar.type, state),
                            SafetyAnnotations.getSafety(typeVar.type.tsym, state));
                    Safety typeArgumentSafety = Safety.mergeAssumingUnknownIsSame(
                            SafetyAnnotations.getSafety(typeArgumentType, state),
                            SafetyAnnotations.getSafety(typeArgumentType.tsym, state));
                    if (!typeVarSafety.allowsAll() && !typeVarSafety.allowsValueWith(typeArgumentSafety)) {
                        // use state.reportMatch to report all failing arguments if multiple are invalid
                        state.reportMatch(buildDescription(tree)
                                .setMessage(String.format(
                                        "Dangerous argument value: arg is '%s' but the parameter requires '%s'.",
                                        typeArgumentSafety, typeVarSafety))
                                .build());
                    }
                }
            }
        }
    }

    private void handleMethodTypeArguments(
            ExpressionTree tree, List<? extends Tree> typeArguments, MethodSymbol methodSymbol, VisitorState state) {
        List<TypeVariableSymbol> typeParameters = methodSymbol.getTypeParameters();
        if (typeParameters == null
                || typeParameters.isEmpty()
                || typeArguments == null
                || typeArguments.isEmpty()
                || typeArguments.size() != typeParameters.size()) {
            return;
        }
        for (int i = 0; i < typeParameters.size(); i++) {
            TypeVariableSymbol parameter = typeParameters.get(i);
            Tree argument = typeArguments.get(i);
            Safety required = Safety.mergeAssumingUnknownIsSame(
                    SafetyAnnotations.getSafety(parameter, state),
                    SafetyAnnotations.getSafety(parameter.type, state),
                    SafetyAnnotations.getSafety(parameter.type.tsym, state));
            Safety given = SafetyAnnotations.getSafety(argument, state);
            if (!required.allowsValueWith(given)) {
                // use state.reportMatch to report all failing arguments if multiple are invalid
                state.reportMatch(buildDescription(tree)
                        .setMessage(String.format(
                                "Dangerous argument value: arg is '%s' but the parameter requires '%s'.",
                                given, required))
                        .build());
            }
        }
    }

    private static SuggestedFix getSuggestedFix(ExpressionTree tree, VisitorState state, Safety argumentSafety) {
        if (SAFE_ARG_OF_METHOD_MATCHER.matches(tree, state) && Safety.UNSAFE.allowsValueWith(argumentSafety)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String unsafeQualifiedClassName = SuggestedFixes.qualifyType(state, fix, UNSAFE_ARG);
            String replacement = String.format("%s.of", unsafeQualifiedClassName);
            return fix.replace(((MethodInvocationTree) tree).getMethodSelect(), replacement)
                    .build();
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
        Safety variableDeclaredSafety = SafetyAnnotations.getSafety(variable, state);
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

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        Tree returnType = tree.getReturnType();
        if (returnType == null) {
            return Description.NO_MATCH;
        }
        Safety methodSafetyAnnotation = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(tree), state);
        if (methodSafetyAnnotation.allowsAll()) {
            return Description.NO_MATCH;
        }
        Safety returnTypeSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(returnType), state);
        if (methodSafetyAnnotation.allowsValueWith(returnTypeSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(returnType)
                .setMessage(String.format(
                        "Dangerous return type: type is '%s' but the method is annotated '%s'.",
                        returnTypeSafety, methodSafetyAnnotation))
                .build();
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        Safety parameterSafetyAnnotation = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(tree), state);
        if (parameterSafetyAnnotation.allowsAll()) {
            return Description.NO_MATCH;
        }
        Safety variableTypeSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(tree.getType()), state);
        if (parameterSafetyAnnotation.allowsValueWith(variableTypeSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Dangerous variable: type is '%s' but the variable is annotated '%s'.",
                        variableTypeSafety, parameterSafetyAnnotation))
                .build();
    }

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        Safety directSafety = SafetyAnnotations.getDirectSafety(ASTHelpers.getSymbol(tree), state);
        if (directSafety.allowsAll()) {
            return Description.NO_MATCH;
        }
        Safety ancestorSafety = SafetyAnnotations.getTypeSafetyFromAncestors(tree, state);
        if (directSafety.allowsValueWith(ancestorSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Dangerous type: annotated '%s' but ancestors declare '%s'.", directSafety, ancestorSafety))
                .build();
    }
}
