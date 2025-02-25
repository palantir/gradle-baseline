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
import com.google.errorprone.util.ASTHelpers.TargetType;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnalysis;
import com.palantir.baseline.errorprone.safety.SafetyAnnotations;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                BugChecker.ClassTreeMatcher,
                BugChecker.LambdaExpressionTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher {

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
        if (path != null) {
            if (path.getLeaf() instanceof MethodTree method) {
                return handleMethodReturn(tree, method, state);
            } else if (path.getLeaf() instanceof LambdaExpressionTree lambda) {
                return handleLambdaReturn(tree, lambda, state);
            }
        }

        return Description.NO_MATCH;
    }

    private Description handleMethodReturn(ReturnTree tree, MethodTree method, VisitorState state) {
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

    private Description handleLambdaReturn(ReturnTree tree, LambdaExpressionTree lambda, VisitorState state) {
        Safety requiredSafety = getLambdaRequiredReturnSafety(lambda, state);

        if (requiredSafety.allowsAll()) {
            // Fast path, all types are accepted, there's no reason to do further analysis.
            return Description.NO_MATCH;
        }
        Safety returnValueSafety =
                SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), tree.getExpression())));
        if (requiredSafety.allowsValueWith(returnValueSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Dangerous return value: result is '%s' but the lambda expects return '%s'.",
                        returnValueSafety, requiredSafety))
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
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);

        // Method annotated safety including all supertypes
        Safety methodCombinedSafety = SafetyAnnotations.getSafety(methodSymbol, state);

        Safety methodExplicitSafety = Safety.mergeAssumingUnknownIsSame(
                SafetyAnnotations.getDirectSafety(methodSymbol, state),
                SafetyAnnotations.getSafety(methodSymbol.getReturnType(), state));
        for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(methodSymbol, state.getTypes())) {
            Safety superMethodSafety = Safety.mergeAssumingUnknownIsSame(
                    SafetyAnnotations.getDirectSafety(superMethod, state),
                    SafetyAnnotations.getSafety(superMethod.getReturnType(), state));
            if (!superMethodSafety.allowsValueWith(methodExplicitSafety)) {
                return buildDescription(returnType)
                        .setMessage(String.format(
                                "Dangerous method override: supertype %s declares '%s' but the method is annotated "
                                        + "'%s'. When this object is cast to the supertype, safety annotations will "
                                        + "not be correct, violating Liskov substitution.",
                                superMethod.owner, superMethodSafety, methodExplicitSafety))
                        .build();
            }
            if (!superMethodSafety.allowsValueWith(methodCombinedSafety)) {
                return buildDescription(returnType)
                        .setMessage(String.format(
                                "Dangerous method override: supertype %s declares '%s' but the method inherits safety "
                                        + "'%s'. When this object is cast to the supertype, safety annotations will "
                                        + "not be correct, violating Liskov substitution.",
                                superMethod.owner, superMethodSafety, methodCombinedSafety))
                        .build();
            }
        }

        if (methodCombinedSafety.allowsAll()) {
            return Description.NO_MATCH;
        }
        Safety returnTypeSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(returnType), state);
        if (methodCombinedSafety.allowsValueWith(returnTypeSafety)) {
            return Description.NO_MATCH;
        }
        return buildDescription(returnType)
                .setMessage(String.format(
                        "Dangerous return type: type is '%s' but the method is annotated '%s'.",
                        returnTypeSafety, methodCombinedSafety))
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
        ClassSymbol classSymbol = ASTHelpers.getSymbol(tree);
        Safety directSafety = SafetyAnnotations.getDirectSafety(classSymbol, state);
        Safety combinedSafety = SafetyAnnotations.getSafety(classSymbol, state);

        if (combinedSafety == Safety.UNKNOWN) {
            return Description.NO_MATCH;
        }
        Set<Type> superTypes = Stream.concat(
                        Stream.of(classSymbol.getSuperclass()), classSymbol.getInterfaces().stream())
                .collect(Collectors.toUnmodifiableSet());
        for (Type superType : superTypes) {
            Safety superTypeSafety = Safety.mergeAssumingUnknownIsSame(
                    SafetyAnnotations.getSafety(superType, state), SafetyAnnotations.getSafety(superType.tsym, state));
            if (superTypeSafety.allowsAll()) {
                continue;
            }
            if (!superTypeSafety.allowsValueWith(directSafety)) {
                return buildDescription(tree)
                        .setMessage(String.format(
                                "Dangerous subtype: supertype %s declares '%s' but the type is annotated "
                                        + "'%s'. When this object is cast to the supertype, safety annotations will "
                                        + "not be correct, violating Liskov substitution.",
                                superType, superTypeSafety, directSafety))
                        .build();
            }
            if (!superTypeSafety.allowsValueWith(combinedSafety)) {
                return buildDescription(tree)
                        .setMessage(String.format(
                                "Dangerous subtype: supertype %s declares '%s' but the type inherits safety "
                                        + "'%s'. When this object is cast to the supertype, safety annotations will "
                                        + "not be correct, violating Liskov substitution.",
                                superType, superTypeSafety, combinedSafety))
                        .build();
            }
        }

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

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree tree, VisitorState state) {
        Safety requiredReturnSafety = getLambdaRequiredReturnSafety(tree, state);

        if (requiredReturnSafety.allowsAll()) {
            // Short-circuit if the return type allows all values
            return Description.NO_MATCH;
        }

        Safety resultSafety = Safety.UNKNOWN;
        switch (tree.getBodyKind()) {
            case EXPRESSION:
                resultSafety = SafetyAnalysis.of(state.withPath(new TreePath(state.getPath(), tree.getBody())));
                break;
            case STATEMENT:
                // Shortcut - statement lambdas get their return type checked in the return statement matcher
                // This also allows us to indicate which return statement is bad (if any) rather than the lambda itself
                return Description.NO_MATCH;
        }

        if (requiredReturnSafety.allowsValueWith(resultSafety)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage(String.format(
                        "Dangerous return value: result is '%s' but the lambda expects return '%s'.",
                        resultSafety, requiredReturnSafety))
                .build();
    }

    private Safety getLambdaRequiredReturnSafety(LambdaExpressionTree tree, VisitorState state) {
        TargetType returnType = ASTHelpers.targetType(state.withPath(new TreePath(state.getPath(), tree)));
        if (returnType == null) {
            return Safety.UNKNOWN;
        }
        return SafetyAnnotations.getSafety(returnType.type(), state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        // This is the type the reference gets "cast" into, whether through assignment, return type, argument, etc
        Type castType = state.getTypes().findDescriptorType(ASTHelpers.getType(tree));
        // The reference will be used as the expected type. This means:
        //   * the safety of the return type of the expected must be "lower" than the actual safety of the reference
        //   * the safety of the arguments of the expected must be "higher" than the actual safety of the reference
        // i.e. "Supplier<@Safe String> f = this::method" shouldn't be allowed, when method returns unsafe
        // Similarly, "@Consumer<@Unsafe String> f = this::method" shouldn't be allowed, when method expects to take a
        //   safe argument (since it might log it)

        Safety castTypeReturnSafety = SafetyAnnotations.getSafety(castType.getReturnType(), state);

        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        Safety referenceReturnSafety = Safety.mergeAssumingUnknownIsSame(
                // This gets the combined safety annotations of the method and any of its supers
                SafetyAnnotations.getSafety(methodSymbol, state),
                SafetyAnnotations.getSafety(methodSymbol.getReturnType(), state));

        // The reference will get used as the cast type
        // This means that we expect the cast type's return type safety and will use it as such
        // If e.g. the cast type says it will return SAFE, but the reference returns UNSAFE, that's a problem
        // On the other hand, if the cast returns UNSAFE and the reference returns SAFE, that's fine
        if (!castTypeReturnSafety.allowsValueWith(referenceReturnSafety)) {
            return buildDescription(tree)
                    .setMessage(String.format(
                            "Dangerous method reference: expected return type '%s' but the reference returns '%s'.",
                            castTypeReturnSafety, referenceReturnSafety))
                    .build();
        }

        if (methodSymbol.getParameters().size() != castType.getParameterTypes().size()) {
            // This is unexpected, as the code should pass compilation - we don't know how to handle it, so just ignore
            return Description.NO_MATCH;
        }

        // This is similar to the return type check, but for each parameter
        // In this case, the requirement is reversed
        // If the cast type says it accepts an UNSAFE parameter, but the reference needs a SAFE one,
        //   then this should break
        // If the cast type accepts SAFE, and the reference needs UNSAFE, that's fine, since we're less permissive
        for (int i = 0; i < methodSymbol.getParameters().size(); i++) {
            Type expectedParameterType = castType.getParameterTypes().get(i);
            Safety expectedParameterSafety = SafetyAnnotations.getSafety(expectedParameterType, state);

            VarSymbol parameter = methodSymbol.getParameters().get(i);
            Type referenceParameterType = parameter.type;
            Safety referenceParameterSafety = SafetyAnnotations.getSafety(referenceParameterType, state);

            if (!referenceParameterSafety.allowsValueWith(expectedParameterSafety)) {
                // use state.reportMatch to report all failing arguments if multiple are invalid
                state.reportMatch(buildDescription(tree)
                        .setMessage(String.format(
                                "Dangerous method reference: method reference expects argument %d with safety '%s', "
                                        + "but will be passed '%s'",
                                i, referenceParameterSafety, expectedParameterSafety))
                        .build());
            }
        }

        return Description.NO_MATCH;
    }
}
