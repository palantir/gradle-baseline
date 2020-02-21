/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.Collection;
import java.util.Deque;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

@AutoService(BugChecker.class)
@BugPattern(
        name = "StrictCollectionIncompatibleType",
        // Idea provides a similar check, avoid noise when that warning is already suppressed.
        // https://github.com/JetBrains/intellij-community/blob/master/java/java-analysis-impl/src/com/intellij/codeInspection/miscGenerics/SuspiciousCollectionsMethodCallsInspection.java
        altNames = {"SuspiciousMethodCalls", "CollectionIncompatibleType"},
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Likely programming error due to using incompatible types as "
                + "arguments for a collection method that accepts Object.")
public final class StrictCollectionIncompatibleType extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    // Collection Types
    private static final String COLLECTION = Collection.class.getName();
    private static final String DEQUE = Deque.class.getName();
    private static final String DICTIONARY = Dictionary.class.getName();
    private static final String LIST = List.class.getName();
    private static final String MAP = Map.class.getName();
    private static final String STACK = Stack.class.getName();
    private static final String VECTOR = Vector.class.getName();
    // Functional Types
    private static final String FUNCTION = Function.class.getName();
    private static final String PREDICATE = Predicate.class.getName();

    private final ImmutableList<IncompatibleTypeMatcher> matchers = ImmutableList.of(
            // Matched patterns are based error-prone CollectionIncompatibleType
            // https://github.com/google/error-prone/blob/master/core/src/main/java/com/google/errorprone/bugpatterns/collectionincompatibletype/CollectionIncompatibleType.java
            compatibleArgType(MAP, "containsKey(java.lang.Object)", 0, 0),
            compatibleArgType(MAP, "containsValue(java.lang.Object)", 1, 0),
            compatibleArgType(MAP, "get(java.lang.Object)", 0, 0),
            compatibleArgType(MAP, "getOrDefault(java.lang.Object,V)", 0, 0),
            compatibleArgType(MAP, "remove(java.lang.Object)", 0, 0),
            compatibleArgType(COLLECTION, "contains(java.lang.Object)", 0, 0),
            compatibleArgType(COLLECTION, "remove(java.lang.Object)", 0, 0),
            compatibleArgType(DEQUE, "removeFirstOccurrence(java.lang.Object)", 0, 0),
            compatibleArgType(DEQUE, "removeLastOccurrence(java.lang.Object)", 0, 0),
            compatibleArgType(DICTIONARY, "get(java.lang.Object)", 0, 0),
            compatibleArgType(DICTIONARY, "remove(java.lang.Object)", 0, 0),
            compatibleArgType(LIST, "indexOf(java.lang.Object)", 0, 0),
            compatibleArgType(LIST, "lastIndexOf(java.lang.Object)", 0, 0),
            compatibleArgType(STACK, "search(java.lang.Object)", 0, 0),
            compatibleArgType(VECTOR, "indexOf(java.lang.Object,int)", 0, 0),
            compatibleArgType(VECTOR, "lastIndexOf(java.lang.Object,int)", 0, 0),
            compatibleArgType(VECTOR, "removeElement(java.lang.Object)", 0, 0));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Return the description from the first matching IncompatibleTypeMatcher
        for (int i = 0; i < matchers.size(); i++) {
            IncompatibleTypeMatcher matcher = matchers.get(i);
            Optional<Description> result = matcher.describe(tree, state);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        // Return the description from the first matching IncompatibleTypeMatcher
        for (int i = 0; i < matchers.size(); i++) {
            IncompatibleTypeMatcher matcher = matchers.get(i);
            Optional<Description> result = matcher.describe(tree, state);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return Description.NO_MATCH;
    }

    @Nullable
    private static Type getBoxedResult(ExpressionTree expressionTree, VisitorState state) {
        Type rawType = ASTHelpers.getResultType(expressionTree);
        if (rawType == null) {
            return null;
        }
        return state.getTypes().boxedTypeOrType(rawType);
    }

    @Nullable
    private static Type getTargetTypeAsSuper(MethodInvocationTree tree, String superTarget, VisitorState state) {
        Type targetMapType = getTargetType(tree);
        if (targetMapType == null) {
            return null;
        }
        Symbol mapSymbol = state.getSymbolFromString(superTarget);
        if (mapSymbol == null) {
            return null;
        }
        return state.getTypes().asSuper(targetMapType, mapSymbol);
    }

    @Nullable
    private static Type getTargetTypeAsSuper(MemberReferenceTree tree, String superTarget, VisitorState state) {
        ExpressionTree targetExpressionTree = tree.getQualifierExpression();
        if (targetExpressionTree == null) {
            return null;
        }
        Type targetMapType = ASTHelpers.getResultType(targetExpressionTree);
        if (targetMapType == null) {
            return null;
        }
        Symbol mapSymbol = state.getSymbolFromString(superTarget);
        if (mapSymbol == null) {
            return null;
        }
        return state.getTypes().asSuper(targetMapType, mapSymbol);
    }

    @Nullable
    private static Type getTargetType(MethodInvocationTree tree) {
        ExpressionTree methodSelect = tree.getMethodSelect();
        if (methodSelect instanceof MemberSelectTree) {
            MemberSelectTree memberSelectTree = (MemberSelectTree) methodSelect;
            return ASTHelpers.getResultType(memberSelectTree.getExpression());
        }
        return null;
    }

    private IncompatibleTypeMatcher compatibleArgType(
            String baseType, String signature, int typeArgumentIndex, int argumentIndex) {
        // Eagerly create the matcher to avoid allocation for each check
        Matcher<ExpressionTree> methodMatcher =
                MethodMatchers.instanceMethod().onDescendantOf(baseType).withSignature(signature);
        return new IncompatibleTypeMatcher() {

            @Override
            public Optional<Description> describe(MethodInvocationTree tree, VisitorState state) {
                if (!methodMatcher.matches(tree, state)) {
                    // This matcher does not apply
                    return Optional.empty();
                }
                if (tree.getArguments().size() <= argumentIndex) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                Type targetType = getTargetTypeAsSuper(tree, baseType, state);
                if (targetType == null) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                if (targetType.getTypeArguments().size() <= typeArgumentIndex) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                Type typeArgumentType = targetType.getTypeArguments().get(typeArgumentIndex);
                ExpressionTree argumentTree = tree.getArguments().get(argumentIndex);
                Type argumentType = getBoxedResult(argumentTree, state);
                if (argumentType == null) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                if (typesCompatible(argumentType, typeArgumentType, state)) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                return Optional.of(buildDescription(argumentTree)
                        .setMessage("Likely programming error due to using incompatible types as arguments for "
                                + "a collection method that accepts Object. Value '"
                                + state.getSourceForNode(argumentTree)
                                + "' of type '"
                                + prettyType(argumentType)
                                + "' is not compatible with the expected type '"
                                + prettyType(typeArgumentType)
                                + '\'')
                        .build());
            }

            @Override
            public Optional<Description> describe(MemberReferenceTree tree, VisitorState state) {
                if (!methodMatcher.matches(tree, state)) {
                    // This matcher does not apply
                    return Optional.empty();
                }
                if (tree.getMode() != MemberReferenceTree.ReferenceMode.INVOKE) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                Type targetType = getTargetTypeAsSuper(tree, baseType, state);
                if (targetType == null) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                if (targetType.getTypeArguments().size() <= typeArgumentIndex) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                Type typeArgumentType = targetType.getTypeArguments().get(typeArgumentIndex);
                Type rawArgumentType = getFunctionalInterfaceArgumentType(tree, argumentIndex, state);
                if (rawArgumentType == null) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                Type argumentType = state.getTypes().boxedTypeOrType(rawArgumentType);
                if (typesCompatible(argumentType, typeArgumentType, state)) {
                    return IncompatibleTypeMatcher.NO_MATCH;
                }
                return Optional.of(buildDescription(tree)
                        .setMessage("Likely programming error due to using incompatible types as arguments for "
                                + "a collection method that accepts Object. Type '"
                                + prettyType(argumentType)
                                + "' is not compatible with the expected type '"
                                + prettyType(typeArgumentType)
                                + '\'')
                        .build());
            }
        };
    }

    private static boolean typesCompatible(Type argumentType, Type typeArgumentType, VisitorState state) {
        // Check erased types only to avoid more complex edge cases. This way we only warn when we
        // have high confidence something isn't right.
        // This tests that types are within the same (linear) inheritance hierarchy, but does not
        // not accept types with a common ancestor.
        return ASTHelpers.isSubtype(argumentType, typeArgumentType, state)
                // Check the reverse direction as well, this allows 'Object' to succeed for
                // delegation, as well as most false positives without sacrificing many known
                // failure cases.
                || ASTHelpers.isSubtype(typeArgumentType, argumentType, state);
    }

    @Nullable
    private static Type getFunctionalInterfaceArgumentType(
            MemberReferenceTree tree, int argumentIndex, VisitorState state) {
        Type resultType = ASTHelpers.getResultType(tree);
        if (resultType == null) {
            return null;
        }

        if (!isSupportedFunctionalInterface(resultType, state)) {
            return null;
        }
        if (resultType.getTypeArguments().size() <= argumentIndex) {
            // Not enough information, it's possible we can
            // inspect the resolved symbol in a future change.
            return null;
        }
        return resultType.getTypeArguments().get(argumentIndex);
    }

    // We don't have a great way to check types on arbitrary interfaces, currently
    // we specifically support common types used in streams.
    private static boolean isSupportedFunctionalInterface(Type type, VisitorState state) {
        // We don't use subtype checks because it's possible for interfaces to extend Function with a default
        // apply implementation, expecting a type that doesn't match function type variables.
        return ASTHelpers.isSameType(type, state.getTypeFromString(FUNCTION), state)
                || ASTHelpers.isSameType(type, state.getTypeFromString(PREDICATE), state);
    }

    /**
     * Pretty prints the input type for use in description messages. This is not suitable for suggested fixes because
     * unlike {@link SuggestedFixes#prettyType(VisitorState, SuggestedFix.Builder, Type)} with non-null state and
     * builder, it doesn't add relevant imports.
     */
    private static String prettyType(Type type) {
        return MoreSuggestedFixes.prettyType(null, null, type);
    }

    private interface IncompatibleTypeMatcher {
        /**
         * Signals that a matcher applied to the input, but did not find any bugs. It is not necessary to check
         * additional {@link IncompatibleTypeMatcher matchers}.
         */
        Optional<Description> NO_MATCH = Optional.of(Description.NO_MATCH);

        /**
         * Returns an empty optional if the provided {@link MethodInvocationTree} isn't matched. If the method is
         * matched, an {@link Optional} of {@link Description#NO_MATCH} is returned for valid use.
         */
        Optional<Description> describe(MethodInvocationTree tree, VisitorState state);

        Optional<Description> describe(MemberReferenceTree tree, VisitorState state);
    }
}
