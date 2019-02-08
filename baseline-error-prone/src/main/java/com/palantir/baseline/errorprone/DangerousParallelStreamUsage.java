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
    private static final String MORE_STREAMS_URL = "https://github.com/palantir/streams/"
            + "blob/1.9.1/src/main/java/com/palantir/common/streams/MoreStreams.java#L53";
    private static final String ERROR_MESSAGE = "Should not use .parallel() on a Java stream. "
            + "Doing so has very subtle and potentially severe performance implications, so in general you're better "
            + "off using " + MORE_STREAMS_URL + " which allows you to provide your own executor service and "
            + "specify the desired parallelism (i.e. the max number of concurrent tasks that will be submitted).\n"
            + "On the contrary the implementation of Java parallel streams uses a globally shared ForkJoinPool and "
            + "does not allow you to provide your own pool. Fork/join pools implement work-stealing, where any thread "
            + "might steal a task from a different thread's queue when blocked waiting for a subtask to complete. "
            + "This might not seem like an issue at first glance, but if you use .parallel() for short tasks "
            + "extensively throughout your codebase and later on you add one piece of code that uses .parallel() "
            + "for long (e.g. I/O) tasks, the other parts of your codebase that use .parallel(), and that you'd "
            + "expect to have consistent performance, might experience performance degradation for no apparent reason. "
            + "The reason is work stealing.\n"
            + "You can suppress this warning if you are certain that all your code will always only use .parallel() "
            + "for short tasks, but even then, you have no real control over the level of parallelism, so you're "
            + "still better off using MoreStreams (linked above)\n"
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

        // Fail on any 'parallel(...)' implementation, regardless of how many parameters it takes
        return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
    }
}
