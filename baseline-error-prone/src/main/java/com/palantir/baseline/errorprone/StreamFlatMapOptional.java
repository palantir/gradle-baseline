/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "`Stream.mapMulti(Optional::ifPresent)` is more efficient than `Stream.flatMap(Optional::stream)`")
public final class StreamFlatMapOptional extends BugChecker implements MethodInvocationTreeMatcher {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAM_FLATMAP_OPTIONAL_STREAM = Matchers.methodInvocation(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getCanonicalName())
                    .namedAnyOf("flatMap")
                    .withParameters(Function.class.getName()),
            // Any of the three MatchTypes are reasonable in this case, given a single arg
            MatchType.AT_LEAST_ONE,
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Optional.class.getCanonicalName())
                    .named("stream")
                    .withNoParameters());

    private static final Matcher<ExpressionTree> STREAM_FILTER_IS_PRESENT = Matchers.methodInvocation(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getCanonicalName())
                    .named("filter")
                    .withParameters(Predicate.class.getName()),
            // Any of the three MatchTypes are reasonable in this case, given a single arg
            MatchType.AT_LEAST_ONE,
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Optional.class.getCanonicalName())
                    .named("isPresent")
                    .withNoParameters());

    private static final Matcher<ExpressionTree> STREAM_MAP_GET = Matchers.methodInvocation(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getCanonicalName())
                    .named("map")
                    .withParameters(Function.class.getName()),
            // Any of the three MatchTypes are reasonable in this case, given a single arg
            MatchType.AT_LEAST_ONE,
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Optional.class.getCanonicalName())
                    .namedAnyOf("get", "orElseThrow")
                    .withNoParameters());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STREAM_FLATMAP_OPTIONAL_STREAM.matches(tree, state)) {
            ExpressionTree methodSelect = tree.getMethodSelect();
            ExpressionTree receiver = ASTHelpers.getReceiver(methodSelect);
            return fix(tree, state, receiver, receiver);
        }

        if (STREAM_MAP_GET.matches(tree, state)) {
            ExpressionTree mapTree = ASTHelpers.getReceiver(tree.getMethodSelect());
            if (mapTree != null && STREAM_FILTER_IS_PRESENT.matches(mapTree, state)) {
                ExpressionTree filterTree = ASTHelpers.getReceiver(mapTree);
                return fix(tree, state, tree.getMethodSelect(), filterTree);
            }
        }

        return Description.NO_MATCH;
    }

    private Description fix(
            MethodInvocationTree tree,
            VisitorState state,
            @Nullable ExpressionTree receiver,
            @Nullable ExpressionTree expressionTree) {
        if (receiver == null || expressionTree == null) {
            return Description.NO_MATCH;
        }

        Type elementType = ASTHelpers.getType(tree);
        if (elementType == null) {
            return Description.NO_MATCH;
        }

        Type receiverType = ASTHelpers.getType(receiver);
        if (receiverType == null) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fix = SuggestedFix.builder();

        String qualifiedType = "";
        if (needsQualification(receiver, expressionTree, elementType)) {
            qualifiedType = qualifyType(state, fix, receiverType.getTypeArguments());
            if (qualifiedType.isEmpty()) {
                qualifiedType = qualifyType(state, fix, Collections.singleton(elementType));
            }
        }

        String replacement = "." + qualifiedType + "mapMulti("
                + SuggestedFixes.qualifyType(state, fix, Optional.class.getCanonicalName()) + "::ifPresent)";
        return buildDescription(tree)
                .addFix(fix.replace(state.getEndPosition(expressionTree), state.getEndPosition(tree), replacement)
                        .build())
                .build();
    }

    private static boolean needsQualification(
            ExpressionTree receiver, ExpressionTree expressionTree, Type elementType) {
        long receiverArgCount = args(ASTHelpers.getReceiverType(receiver)).count();
        return args(elementType).findAny().isPresent()
                || receiverArgCount > 1
                || (receiverArgCount == 0
                        && args(ASTHelpers.getReceiverType(expressionTree))
                                .findAny()
                                .isPresent())
                || (receiverArgCount == 0 && !elementType.getTypeArguments().isEmpty());
    }

    private static String qualifyType(
            VisitorState state, SuggestedFix.Builder fix, Collection<Type> receiverTypeArguments) {
        if (receiverTypeArguments.isEmpty()
                || args(receiverTypeArguments).findAny().isEmpty()) {
            return "";
        }
        return args(receiverTypeArguments)
                .map(type -> SuggestedFixes.qualifyType(state, fix, type))
                .collect(Collectors.joining(", ", "<", ">"));
    }

    private static Stream<Type> args(Type type) {
        return args(type.getTypeArguments());
    }

    static Stream<Type> args(Collection<Type> types) {
        return types.stream().flatMap(t -> t.getTypeArguments().stream());
    }
}
