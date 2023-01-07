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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Forbid gradle apis that eagerly create tasks. Learn more at"
                + " https://docs.gradle.org/current/userguide/task_configuration_avoidance.html")
@SuppressWarnings("deprecation")
public final class GradleTaskConfigurationAvoidance extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Avoid creating tasks eagerly. Learn more at"
            + " https://docs.gradle.org/current/userguide/task_configuration_avoidance.html";

    private static final Matcher<ExpressionTree> TASK_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Project")
            .namedAnyOf("task");

    private static final Matcher<ExpressionTree> TASK_CONTAINER_METHODS = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("create", "getByPath", "findByPath");

    private static final Matcher<ExpressionTree> TASK_COLLECTION_METHODS = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskCollection")
            .namedAnyOf("getByName", "whenTaskAdded", "getAt");

    // These methods are defined for DomainObjectCollection, but we only care about Tasks.
    // Match based on arguments. withType(Class) is fine.
    private static final Matcher<ExpressionTree> TASKS_WITH_TYPE_METHOD_ACTION = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskCollection")
            .namedAnyOf("withType")
            .withParameters("java.lang.Class", "org.gradle.api.Action");
    private static final Matcher<ExpressionTree> TASKS_WITH_TYPE_METHOD_CLOSURE = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskCollection")
            .namedAnyOf("withType")
            .withParameters("java.lang.Class", "groovy.lang.Closure");

    // These methods are defined for DomainObjectCollection, but we only care about Tasks.
    private static final Matcher<ExpressionTree> DOMAIN_OBJECT_COLLECTION_METHODS = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskCollection")
            .namedAnyOf("all", "whenObjectAdded");

    // These methods are defined for NamedDomainObjectCollection, but we only care about Tasks.
    private static final Matcher<ExpressionTree> NAMED_DOMAIN_OBJECT_COLLECTION_METHODS =
            MethodMatchers.instanceMethod()
                    .onDescendantOf("org.gradle.api.tasks.TaskCollection")
                    .namedAnyOf("findByName");

    private static final Matcher<ExpressionTree> MATCHER = Matchers.anyOf(
            TASK_METHOD,
            TASK_CONTAINER_METHODS,
            TASK_COLLECTION_METHODS,
            TASKS_WITH_TYPE_METHOD_ACTION,
            TASKS_WITH_TYPE_METHOD_CLOSURE,
            DOMAIN_OBJECT_COLLECTION_METHODS,
            NAMED_DOMAIN_OBJECT_COLLECTION_METHODS);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            return buildDescription(tree).setMessage(MESSAGE).build();
        }

        return Description.NO_MATCH;
    }
}
