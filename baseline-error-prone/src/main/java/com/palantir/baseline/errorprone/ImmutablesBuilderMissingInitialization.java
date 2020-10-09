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
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
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
    // Prefixes on the interface getter methods that may be stripped by immutables - by default only GET_ is stripped,
    // but some places set the style to remove IS_ too and we can't load the style to check what to remove. It doesn't
    // matter if we remove too much, because we only do a suffix match on the methods.
    private static final ImmutableSet<String> GET_PREFIXES = ImmutableSet.of("GET_", "IS_");

    private static final Matcher<ExpressionTree> builderMethodMatcher = Matchers.instanceMethod()
            .onClass(ImmutablesBuilderMissingInitialization::extendsImmutablesGeneratedClass)
            .named("build")
            .withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Check that the method is a builder's build() method
        if (!builderMethodMatcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ClassSymbol builderClass = ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree));
        ClassSymbol immutableClass = ASTHelpers.enclosingClass(builderClass);
        if (immutableClass == null) {
            return Description.NO_MATCH;
        }
        Optional<ClassSymbol> interfaceClass = Streams.concat(
                        immutableClass.getInterfaces().stream(), Stream.of(immutableClass.getSuperclass()))
                .map(type -> type.tsym)
                .map(filterByType(ClassSymbol.class))
                .flatMap(Streams::stream)
                .filter(classSymbol ->
                        ASTHelpers.hasAnnotation(classSymbol, "org.immutables.value.Value.Immutable", state))
                .findAny();
        if (!interfaceClass.isPresent()) {
            return Description.NO_MATCH;
        }

        // Mandatory fields have a private static final constant in the generated builder named INIT_BIT_varname, where
        // varname is the UPPER_UNDERSCORE version of the variable name. Find these fields to get the mandatory fields.
        Set<String> requiredFields = Streams.stream(builderClass.members().getSymbols())
                .filter(Symbol::isStatic)
                .filter(symbol -> symbol.getKind().isField())
                .filter(symbol -> symbol.getSimpleName().toString().startsWith(FIELD_INIT_BITS_PREFIX))
                .map(Symbol::toString)
                .map(initBitsName -> removeFromStart(initBitsName, FIELD_INIT_BITS_PREFIX))
                .map(fieldName -> GET_PREFIXES.stream().reduce(fieldName, this::removeFromStart))
                .map(CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL))
                .collect(Collectors.toSet());

        if (!checkAllFieldsCanBeInitialized(requiredFields, builderClass)) {
            // There is likely a custom style applied that means the rules don't match
            return Description.NO_MATCH;
        }

        // Run the check
        Set<String> uninitializedFields = checkInitialization(
                ASTHelpers.getReceiver(tree),
                requiredFields,
                state,
                builderClass,
                immutableClass,
                interfaceClass.get());

        if (uninitializedFields.isEmpty()) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage(String.format(
                        "Some builder fields have not been initialized: %s",
                        Joiner.on(", ").join(uninitializedFields)))
                .build();
    }

    /**
     * Recursively check that all of the uninitializedFields are initialized in the expression, returning any that
     * are not.
     */
    private Set<String> checkInitialization(
            ExpressionTree tree,
            Set<String> uninitializedFields,
            VisitorState state,
            ClassSymbol builderClass,
            ClassSymbol immutableClass,
            ClassSymbol interfaceClass) {
        if (uninitializedFields.isEmpty()) {
            return ImmutableSet.of();
        }
        if (tree instanceof MethodInvocationTree) {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol((MethodInvocationTree) tree);
            if (!Objects.equals(methodSymbol.enclClass(), builderClass)) {
                // This method belongs to a class other than the builder, so we are at the end of the chain
                // If the only thing this method does is construct and return the builder, we continue and require
                // everything to have been initialized at this point, but if it does anything more complex then give up
                if (methodJustConstructsBuilder(methodSymbol, state, immutableClass, interfaceClass)) {
                    return uninitializedFields;
                } else {
                    return ImmutableSet.of();
                }
            }
            if (methodSymbol.getSimpleName().contentEquals("from")) {
                // `from` initializes all fields, so we don't need to continue
                return ImmutableSet.of();
            }

            return checkInitialization(
                    ASTHelpers.getReceiver(tree),
                    removeFieldsPotentiallyInitializedBy(
                            uninitializedFields, methodSymbol.getSimpleName().toString()),
                    state,
                    builderClass,
                    immutableClass,
                    interfaceClass);
        } else if (tree instanceof NewClassTree) {
            NewClassTree newClassTree = (NewClassTree) tree;
            if (newClassTree.getArguments().isEmpty()) {
                // The constructor returned the builder (otherwise we would have bailed out in a previous iteration), so
                // we should have seen all the field initializations
                return uninitializedFields;
            }
            // If the constructor takes arguments, it's doing something funky
            return ImmutableSet.of();
        }

        // If the chain started with something other than a method call or constructor to create the builder, give up
        return ImmutableSet.of();
    }

    /**
     * Check that every required field has a setter method, to ensure that there are no style rules that have not been
     * accounted for here.
     */
    private boolean checkAllFieldsCanBeInitialized(Set<String> fields, Symbol builderClass) {
        return Streams.stream(builderClass.members().getSymbols())
                .map(filterByType(MethodSymbol.class))
                .flatMap(Streams::stream)
                .filter(symbol -> !symbol.isStaticOrInstanceInit()
                        && !symbol.isConstructor()
                        && !symbol.isAnonymous()
                        && symbol.getParameters().size() == 1)
                .map(symbol -> symbol.getSimpleName().toString())
                .reduce(fields, this::removeFieldsPotentiallyInitializedBy, Sets::intersection)
                .isEmpty();
    }

    /**
     * Takes a set of uninitialized fields, and returns a set containing the fields that cannot have been initialized by
     * the method methodName.
     */
    private Set<String> removeFieldsPotentiallyInitializedBy(Set<String> uninitializedFields, String methodName) {
        String methodNameLowerCase = methodName.toLowerCase();
        return uninitializedFields.stream()
                .filter(fieldName -> !methodNameLowerCase.endsWith(fieldName.toLowerCase()))
                .collect(Collectors.toSet());
    }

    /**
     * Make sure a method only does one thing, which is to call a constructor with no arguments and return the result.
     *
     * We don't check which class's constructor because it must return something compatible with Builder for us to have
     * got this far, and that's all we care about.
     */
    private boolean methodJustConstructsBuilder(
            MethodSymbol methodSymbol, VisitorState state, ClassSymbol immutableClass, ClassSymbol interfaceClass) {
        MethodTree methodTree = ASTHelpers.findMethod(methodSymbol, state);
        if (methodTree != null) {
            // Check that the method just contains one statement, which is of the form `return new Something();` or
            // `return ImmutableType.builder();`
            if (methodTree.getBody().getStatements().size() != 1) {
                return false;
            }
            return methodTree.getBody().getStatements().stream()
                    .findAny()
                    .flatMap(filterByType(ReturnTree.class))
                    .map(ReturnTree::getExpression)
                    .map(expressionTree -> {
                        if (expressionTree instanceof NewClassTree) {
                            // To have got here, the return type must be compatible with ImmutableType, so we don't need
                            // to check the class being constructed
                            return ((NewClassTree) expressionTree)
                                    .getArguments()
                                    .isEmpty();
                        } else if (expressionTree instanceof MethodInvocationTree) {
                            return Optional.ofNullable(ASTHelpers.getSymbol(expressionTree))
                                    .flatMap(filterByType(MethodSymbol.class))
                                    .filter(symbol -> symbol.getParameters().isEmpty())
                                    .filter(symbol -> symbol.getSimpleName().contentEquals("builder"))
                                    .map(Symbol::enclClass)
                                    .flatMap(filterByType(ClassSymbol.class))
                                    .filter(symbol -> symbol.equals(immutableClass))
                                    .isPresent();
                        } else {
                            return false;
                        }
                    })
                    .orElse(false);
        }
        // The method that was called is in a different compilation unit, so we can't access the source.
        // If the method is .builder() and it's on a trusted class (the immutable implementation or the interface that
        // it was generated from), assume that it just constructs the builder and nothing else.
        return ((Objects.equals(methodSymbol.enclClass(), immutableClass)
                        || Objects.equals(methodSymbol.enclClass(), interfaceClass))
                && methodSymbol.getSimpleName().contentEquals("builder")
                && methodSymbol.getParameters().isEmpty());
    }

    /**
     * If input starts with toRemove, returns the rest of input with toRemove removed, otherwise just returns input.
     */
    private String removeFromStart(String input, String toRemove) {
        if (input.startsWith(toRemove)) {
            return input.substring(toRemove.length());
        }
        return input;
    }

    /**
     * Returns a function for use in Optional.flatMap that filters by type, and casts to that type.
     */
    private <I, O extends I> Function<I, Optional<O>> filterByType(Class<O> clazz) {
        return value -> {
            if (clazz.isInstance(value)) {
                return Optional.of(clazz.cast(value));
            }
            return Optional.empty();
        };
    }

    /**
     * Returns whether the provided type is a class that was generated by Immutables, or extends a class that was.
     */
    private static boolean extendsImmutablesGeneratedClass(Type type, VisitorState state) {
        if (type.tsym instanceof ClassSymbol) {
            return Optional.ofNullable(type.tsym.getAnnotation(Generated.class))
                    .map(generated -> generated.generator().equals("Immutables"))
                    .orElseGet(() -> extendsImmutablesGeneratedClass(((ClassSymbol) type.tsym).getSuperclass(), state));
        }
        return false;
    }
}
