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
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousParallelStreamUsage",
        category = Category.JDK,
        severity = SeverityLevel.WARNING,
        summary = "Discourage usage of .parallel() in Java streams.")
public final class DangerousParallelStreamUsage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;
    private static final String ERROR_MESSAGE = "Should not use .parallel() on a Java stream. "
            + "Doing so has very subtle and potentially severe performance implications, so in general you're better "
            + "off either using an ExecutorCompletionService or https://github.com/amaembo/streamex, which allows you"
            + " to provide a non-globally-shared thread pool. "
            + "The implementation of Java parallel streams uses a globally shared ForkJoinPool and does not allow you "
            + "to provide your own pool. Fork/join pools implement work-stealing, where any thread might steal a task "
            + "from a different thread's queue when blocked waiting for a subtask to complete. This might not seem "
            + "like an issue at first glance, but if you use .parallel() for short tasks extensively throughout your "
            + "code base and later on you add one piece of code that uses .parallel() for long (e.g. I/O) tasks, the "
            + "other parts of your codebase that use .parallel(), and that you'd expect to have consistent "
            + "performance, might experience performance degradation for no apparent reason. The reason is work "
            + "stealing. You can suppress this warning if you are certain that all code that will ever use .parallel() "
            + "in your codebase will never create long running tasks, but you are still probably better off using "
            + "https://github.com/amaembo/streamex which allows you to provide your own ForkJoinPool, so you can "
            + "isolate the impact of work stealing (threads can only steal from other threads in the same pool)."
            + "You can find more info here: https://stackoverflow.com/a/54581148/7182570";

    private static final Matcher<ExpressionTree> PARALLEL_CALL_ON_JAVA_STREAM_MATCHER =
            MethodMatchers.instanceMethod()
                    .onDescendantOf("java.util.stream.Stream")
                    .named("parallel");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!PARALLEL_CALL_ON_JAVA_STREAM_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // This is because StreamEx (an potentially other libraries) extends Stream and provide a
        // .parallel(ForkJoinPool yourPool) method, which is ok to use.
        if (tree.getArguments().size() > 0) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
    }
}
