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
import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
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
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.immutables.value.Generated;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesBuilderMissingInitialization",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "__TODO__")
public final class ImmutablesBuilderMissingInitialization extends BugChecker implements MethodInvocationTreeMatcher {
    private static final String FIELD_INIT_BITS_PREFIX = "INIT_BIT_";

    private static final Joiner COMMA_JOINER = Joiner.on(", ");
    private static final Converter<String, String> UPPER_TO_CAMEL_CASE_CONVERTER =
            CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL);
    private static final Matcher<ExpressionTree> BUILDER_METHOD_MATCHER =
            Matchers.instanceMethod().anyClass().named("build").withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Check that the method is a builder's build() method
        if (!BUILDER_METHOD_MATCHER.matches(tree, state)) {
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

        Set<String> missingInitBits = checkInitialization(ASTHelpers.getReceiver(tree), initBits, state, classSymbol);
        if (missingInitBits.isEmpty()) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage(String.format(
                        "Some builder fields have not been initialized: %s",
                        COMMA_JOINER.join(missingInitBits.stream()
                                .map(initBitsName -> initBitsName.replace(FIELD_INIT_BITS_PREFIX, ""))
                                .map(UPPER_TO_CAMEL_CASE_CONVERTER::convert)
                                .iterator())))
                .build();
    }

    private Set<String> checkInitialization(
            ExpressionTree tree, Set<String> uninitializedInitBits, VisitorState state, ClassSymbol builderClass) {
        if (tree != null && tree.getKind().equals(Kind.METHOD_INVOCATION)) {
            MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodInvocationTree);
            ClassSymbol classSymbol = methodSymbol.enclClass();

            // If this is the builder's constructor or a builder method on the parent class, stop here; if it's another
            // method on a different class, give up because it will quickly become impossible to track whether it's
            // been fully initialized
            if (classSymbol == null || !classSymbol.equals(builderClass)) {
                if (methodSymbol.getSimpleName().contentEquals("builder")
                        && methodSymbol.getParameters().isEmpty()) {
                    // No args builder method that returns this type can be assumed to just construct the builder
                    return uninitializedInitBits;
                }
                // If this is any other method, there may be initialization that occurs in the method, and trying to
                // track that down will quickly become impossible, so give up here
                return ImmutableSet.of();
            }

            MethodTree methodTree = ASTHelpers.findMethod(methodSymbol, state);
            Set<String> initializedInitBits = methodTree.getBody().getStatements().stream()
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

            Set<String> remainingInitBits = Sets.difference(uninitializedInitBits, initializedInitBits);
            return checkInitialization(ASTHelpers.getReceiver(tree), remainingInitBits, state, builderClass);
        }

        // If the chain started with something other than a method call to create the builder, give up
        return ImmutableSet.of();
    }

    private <I, O extends I> Function<I, Stream<O>> filterByType(Class<O> clazz) {
        return value -> {
            if (clazz.isInstance(value)) {
                return Stream.of(clazz.cast(value));
            }
            return Stream.empty();
        };
    }
}
