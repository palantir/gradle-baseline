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
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.immutables.value.Generated;

/**
 * Checks that all required fields in an immutables.org generated builder have been populated.
 *
 * To make it decidable, it is limited to builders that are constructed by calling {@code new ImmutableType.Builder()},
 * {@code new Type.Builder()} (where Type.Builder extends ImmutableType.Builder), or a method that only calls one of
 * those constructors and returns the result, and are never stored into a variable. Builders that do not meet these
 * conditions are assumed to populate all fields, and are ignored.
 *
 * Mandatory fields are determined by inspecting the generated builder source to find the initBits that are updated by
 * each method, to find any that do not get set. If Immutables changes the way that they check for required fields, this
 * check will stop working (but the check will probably pass).
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesBuilderMissingInitialization",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "All required fields of an Immutables builder must be initialized")
public final class ImmutablesBuilderMissingInitialization extends BugChecker implements MethodInvocationTreeMatcher {
    private static final String FIELD_INIT_BITS_PREFIX = "INIT_BIT_";

    private static final Matcher<ExpressionTree> builderMethodMatcher =
            Matchers.instanceMethod().anyClass().named("build").withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Check that the method is a builder's build() method
        if (!builderMethodMatcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ClassSymbol classSymbol = ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree));
        if (Optional.ofNullable(classSymbol.getAnnotation(Generated.class))
                .map(annotation -> !annotation.generator().equals("Immutables"))
                .orElse(true)) {
            return Description.NO_MATCH;
        }

        // Find all of the initBits in the generated builder, to determine the mandatory fields
        ClassTree classTree = ASTHelpers.findClass(classSymbol, state);
        if (classTree == null) {
            return Description.NO_MATCH;
        }
        Set<String> initBits = classTree.getMembers().stream()
                .flatMap(memberTree -> {
                    if (memberTree instanceof VariableTree) {
                        return Stream.of((VariableTree) memberTree);
                    }
                    return Stream.empty();
                })
                .filter(variableTree -> variableTree.getName().toString().startsWith(FIELD_INIT_BITS_PREFIX))
                .map(variableTree -> variableTree.getName().toString())
                .collect(Collectors.toSet());

        // Make sure all the init bits get initialized by the preceding method chain
        Set<String> missingInitBits = checkInitialization(ASTHelpers.getReceiver(tree), initBits, state, classSymbol);

        if (missingInitBits.isEmpty()) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Some builder fields have not been initialized: %s",
                        Joiner.on(", ")
                                .join(missingInitBits.stream()
                                        .map(initBitsName -> initBitsName.replace(FIELD_INIT_BITS_PREFIX, ""))
                                        .map(CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL)::convert)
                                        .iterator())))
                .build();
    }

    /**
     * Recursively check that all of the uninitializedInitBits are initialized in the expression, returning any that
     * are not.
     */
    private Set<String> checkInitialization(
            ExpressionTree tree, Set<String> uninitializedInitBits, VisitorState state, ClassSymbol builderClass) {
        if (uninitializedInitBits.isEmpty()) {
            return ImmutableSet.of();
        }
        if (tree instanceof MethodInvocationTree) {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol((MethodInvocationTree) tree);
            MethodTree methodTree = ASTHelpers.findMethod(methodSymbol, state);
            if (methodTree == null) {
                return ImmutableSet.of();
            }
            if (!Objects.equals(methodSymbol.enclClass(), builderClass)) {
                // This method belongs to a class other than the builder, so we are at the end of the chain
                // If the only thing this method does is construct and return the builder, we continue and require
                // everything to have been initialized at this point, but if it does anything more complex then give up
                if (methodJustCallsConstructor(methodTree)) {
                    return uninitializedInitBits;
                }
                return ImmutableSet.of();
            }
            if (methodSymbol.getSimpleName().contentEquals("from")) {
                // `from` initializes all fields, so we don't need to continue
                return ImmutableSet.of();
            }

            Set<String> remainingInitBits = new HashSet<>(uninitializedInitBits);
            remainingInitBits.removeAll(initBitsInitializedBy(methodTree));

            return checkInitialization(ASTHelpers.getReceiver(tree), remainingInitBits, state, builderClass);
        } else if (tree instanceof NewClassTree) {
            NewClassTree newClassTree = (NewClassTree) tree;
            if (newClassTree.getArguments().isEmpty()) {
                // The constructor returned the builder (otherwise we would have bailed out in a previous iteration), so
                // we should have seen all the field initializations
                return uninitializedInitBits;
            }
            // If the constructor takes arguments, it's doing something funky
            return ImmutableSet.of();
        }

        // If the chain started with something other than a method call or constructor to create the builder, give up
        return ImmutableSet.of();
    }

    /**
     * Extracts the init bits that are marked as initialized in this method.
     *
     * Assumes that they are used as {@code initBits &= ~INIT_BIT_VAR}.
     */
    private Set<String> initBitsInitializedBy(MethodTree methodTree) {
        return methodTree.getBody().getStatements().stream()
                .flatMap(filterByType(ExpressionStatementTree.class))
                .map(ExpressionStatementTree::getExpression)
                .flatMap(filterByType(CompoundAssignmentTree.class))
                .filter(assignmentTree -> {
                    if (assignmentTree.getVariable() instanceof IdentifierTree) {
                        return ((IdentifierTree) assignmentTree.getVariable())
                                .getName()
                                .contentEquals("initBits");
                    }
                    return false;
                })
                .map(CompoundAssignmentTree::getExpression)
                .flatMap(filterByType(UnaryTree.class))
                .flatMap(unaryTree -> {
                    if (unaryTree.getKind().equals(Kind.BITWISE_COMPLEMENT)) {
                        return Stream.of(unaryTree.getExpression());
                    }
                    return Stream.empty();
                })
                .flatMap(filterByType(IdentifierTree.class))
                .map(identifierTree -> identifierTree.getName().toString())
                .collect(Collectors.toSet());
    }

    /**
     * Make sure a method only does one thing, which is to call a constructor with no arguments and return the result.
     *
     * We don't check which class's constructor because it must return something compatible with Builder for us to have
     * got this far, and that's all we care about.
     */
    private boolean methodJustCallsConstructor(MethodTree methodTree) {
        if (methodTree.getBody().getStatements().size() != 1) {
            return false;
        }
        return methodTree.getBody().getStatements().stream()
                .flatMap(filterByType(ReturnTree.class))
                .map(ReturnTree::getExpression)
                .flatMap(filterByType(NewClassTree.class))
                .anyMatch(newClassTree -> newClassTree.getArguments().isEmpty());
    }

    /**
     * Returns a function for use in Stream.flatMap that filters by type, and casts to that type.
     */
    private <I, O extends I> Function<I, Stream<O>> filterByType(Class<O> clazz) {
        return value -> {
            if (clazz.isInstance(value)) {
                return Stream.of(clazz.cast(value));
            }
            return Stream.empty();
        };
    }
}
