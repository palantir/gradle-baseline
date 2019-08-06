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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousCompletableFutureUsage",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Disallow CompletableFuture asynchronous operations without an Executor.")
public final class DangerousCompletableFutureUsage
        extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final String ERROR_MESSAGE = "Should not use CompletableFuture methods without specifying a "
            + "custom executor service. Doing so has very subtle and potentially severe performance implications, so "
            + "in you're better off using your own executor service which allows you to provide instrumentation "
            + "and specify the desired parallelism (i.e. the max number of concurrent tasks that will be submitted).\n"
            + "By default, CompletableFuture uses the globally shared ForkJoinPool. Fork/join pools implement "
            + "work-stealing, where any thread might steal a task from a different thread's queue when blocked "
            + "waiting for a subtask to complete. This might not seem like an issue at first glance, but if you rely"
            + "on the ForkJoinPool for short tasks extensively throughout your codebase and later on you add one "
            + "piece of code that uses the pool for long (e.g. I/O) tasks, the other parts of your codebase that"
            + "you'd expect to have consistent performance might experience performance degradation for no apparent "
            + "reason.\n"
            + "If you're absolutely certain that the ForkJoinPool is correct, please pass ForkJoinPool.commonPool()"
            + "directly, ideally with a comment explaining why it is ideal.";

    private static final Matcher<ExpressionTree> SUPPLY_ASYNC = MethodMatchers.staticMethod()
            .onClass(CompletableFuture.class.getName())
            .named("supplyAsync")
            .withParameters(Supplier.class.getName());

    private static final Matcher<ExpressionTree> RUN_ASYNC = MethodMatchers.staticMethod()
            .onClass(CompletableFuture.class.getName())
            .named("runAsync")
            .withParameters(Runnable.class.getName());

    private static final Matcher<ExpressionTree> STATIC_ASYNC_FACTORY_MATCHERS =
            Matchers.anyOf(SUPPLY_ASYNC, RUN_ASYNC);

    private static final Matcher<ExpressionTree> COMPLETION_STAGE_ASYNC_INVOCATION = MethodMatchers.instanceMethod()
            .onDescendantOf(CompletionStage.class.getName())
            .withNameMatching(Pattern.compile(".*Async"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STATIC_ASYNC_FACTORY_MATCHERS.matches(tree, state)
                || isCompletionStageAsyncMethodWithoutExecutor(tree, state)) {
            return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
        }
        return Description.NO_MATCH;
    }

    private static boolean isCompletionStageAsyncMethodWithoutExecutor(MethodInvocationTree tree, VisitorState state) {
        if (COMPLETION_STAGE_ASYNC_INVOCATION.matches(tree, state)) {
            Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
            // Should only happen if there are errors in the AST
            if (symbol != null) {
                List<Type> parameterTypes = symbol.type.getParameterTypes();
                Type lastParameterType = parameterTypes.get(parameterTypes.size() - 1);
                return !ASTHelpers.isSameType(
                        lastParameterType, state.getTypeFromString(Executor.class.getName()), state);
            }
        }
        return false;
    }
}
