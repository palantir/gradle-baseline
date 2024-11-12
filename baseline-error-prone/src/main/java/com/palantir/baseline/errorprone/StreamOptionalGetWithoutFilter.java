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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Stream<Optional<?>> should call filter(Optional::isPresent) before map(Optional::get)",
        explanation = "Calling map(Optional::get) on a Stream<Optional<?>> without first calling "
                + "filter(Optional::isPresent) can cause NoSuchElementException.")
public final class StreamOptionalGetWithoutFilter extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> OPTIONAL_GET_METHOD = Matchers.staticMethod()
            .onClass(Optional.class.getName())
            .named("get")
            .withNoParameters();

    private static final Matcher<ExpressionTree> STREAM_MAP_METHOD = Matchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .named("map")
            .withParameters(Function.class.getName());

    private static final Matcher<ExpressionTree> STREAM_FILTER_METHOD = Matchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .named("filter")
            .withParameters(Predicate.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STREAM_MAP_METHOD.matches(tree, state) && containsOptionalGet(tree, state)) {
            ExpressionTree receiver = ASTHelpers.getReceiver(tree);
            if (receiver != null && !hasFilterIsPresent(receiver, state)) {
                return describeMatch(tree);
            }
        }
        return Description.NO_MATCH;
    }

    private static boolean containsOptionalGet(MethodInvocationTree tree, VisitorState state) {
        return tree.getArguments().stream().anyMatch(arg -> OPTIONAL_GET_METHOD.matches(arg, state));
    }

    private static boolean hasFilterIsPresent(ExpressionTree receiver, VisitorState state) {
        if (receiver instanceof MethodInvocationTree) {
            MethodInvocationTree methodInvocationTree = (MethodInvocationTree) receiver;
            if (STREAM_FILTER_METHOD.matches(methodInvocationTree, state)) {
                return methodInvocationTree.getArguments().stream()
                        .anyMatch(arg -> "Optional::isPresent".equals(state.getSourceForNode(arg)));
            } else {
                return hasFilterIsPresent(methodInvocationTree.getMethodSelect(), state);
            }
        }
        return false;
    }
}
