/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@AutoService(BugChecker.class)
@BugPattern(
        name = "InvocationHandlerDelegation",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "InvocationHandlers which delegate to another object must catch and unwrap "
                + "InvocationTargetException, otherwise an UndeclaredThrowableException will be thrown "
                + "each time the delegate throws an exception.\n"
                + "This check is intended to be advisory. It's fine to "
                + "@SuppressWarnings(\"InvocationHandlerDelegation\") in certain cases, "
                + "but is usually not recommended.")
public final class InvocationHandlerDelegation extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<MethodTree> INVOCATION_HANDLER = Matchers.anyOf(
            Matchers.allOf(
                    Matchers.not(Matchers.isStatic()),
                    MoreMatchers.hasSignature("invoke(java.lang.Object,java.lang.reflect.Method,java.lang.Object[])"),
                    Matchers.enclosingClass(Matchers.isSubtypeOf(InvocationHandler.class.getName()))),
            Matchers.allOf(
                    Matchers.not(Matchers.isStatic()),
                    MoreMatchers.hasSignature(
                            "handleInvocation(java.lang.Object,java.lang.reflect.Method,java.lang.Object[])"),
                    Matchers.enclosingClass(Matchers.isSubtypeOf(AbstractInvocationHandler.class.getName()))));

    private static final Matcher<ExpressionTree> METHOD_INVOKE = MethodMatchers.instanceMethod()
            .onExactClass(Method.class.getName())
            .withSignature("invoke(java.lang.Object,java.lang.Object...)");

    private static final Matcher<ExpressionTree> METHOD_INVOKE_ENCLOSED_BY_INVOCATION_HANDLER =
            Matchers.allOf(METHOD_INVOKE, Matchers.enclosingMethod(INVOCATION_HANDLER));

    private static final Matcher<Tree> CONTAINS_METHOD_INVOKE = Matchers.contains(ExpressionTree.class, METHOD_INVOKE);

    private static final Matcher<ExpressionTree> UNWRAP_THROWABLE = MethodMatchers.instanceMethod()
            .onDescendantOf(Throwable.class.getName())
            .named("getCause")
            .withParameters();

    private static final Matcher<Tree> CONTAINS_UNWRAP_THROWABLE =
            Matchers.contains(ExpressionTree.class, UNWRAP_THROWABLE);

    private static final Matcher<ExpressionTree> UNWRAP_ITE = MethodMatchers.instanceMethod()
            .onDescendantOf(InvocationTargetException.class.getName())
            // getTargetException is deprecated, but does work correctly.
            .namedAnyOf("getCause", "getTargetException")
            .withParameters();

    private static final Matcher<ExpressionTree> PASS_ITE = Matchers.methodInvocation(
            Matchers.anyMethod(),
            ChildMultiMatcher.MatchType.AT_LEAST_ONE,
            Matchers.isSubtypeOf(InvocationTargetException.class));

    private static final Matcher<Tree> CONTAINS_INSTANCEOF_ITE = Matchers.contains(
            InstanceOfTree.class,
            (instanceOfTree, state) -> ASTHelpers.isSameType(
                    ASTHelpers.getType(instanceOfTree.getType()),
                    state.getTypeFromString(InvocationTargetException.class.getName()),
                    state));

    private static final Matcher<Tree> CONTAINS_UNWRAP_ITE = Matchers.anyOf(
            Matchers.contains(ExpressionTree.class, UNWRAP_ITE),
            Matchers.contains(ExpressionTree.class, PASS_ITE),
            Matchers.contains(IfTree.class, (Matcher<IfTree>)
                    (ifExpression, state) -> CONTAINS_INSTANCEOF_ITE.matches(ifExpression.getCondition(), state)
                            && CONTAINS_UNWRAP_THROWABLE.matches(ifExpression.getThenStatement(), state)),
            Matchers.contains(ConditionalExpressionTree.class, (Matcher<ConditionalExpressionTree>)
                    (ifExpression, state) -> CONTAINS_INSTANCEOF_ITE.matches(ifExpression.getCondition(), state)
                            && CONTAINS_UNWRAP_THROWABLE.matches(ifExpression.getTrueExpression(), state)));

    private static final Matcher<MethodTree> HANDLES_ITE = Matchers.anyOf(
            Matchers.contains(TryTree.class, (Matcher<TryTree>)
                    (tree, state) -> CONTAINS_METHOD_INVOKE.matches(tree.getBlock(), state)
                            && tree.getCatches().stream()
                                    .anyMatch(catchTree -> CONTAINS_UNWRAP_ITE.matches(catchTree.getBlock(), state))),
            // If Method.invoke occurs in a lambda or anonymous class, we don't have enough
            // conviction that it's a bug.
            Matchers.contains(LambdaExpressionTree.class, CONTAINS_METHOD_INVOKE::matches),
            Matchers.contains(NewClassTree.class, CONTAINS_METHOD_INVOKE::matches));

    private static final Matcher<MethodInvocationTree> MATCHER = Matchers.allOf(
            METHOD_INVOKE_ENCLOSED_BY_INVOCATION_HANDLER, Matchers.not(Matchers.enclosingMethod(HANDLES_ITE)));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
