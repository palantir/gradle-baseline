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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.immutables.value.Value;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "All default methods in an Immutable class that are annotated with @JsonProperty should specify"
                + " @Value.Default annotation")
public final class ImmutableDefaultShouldHaveAnnotations extends BugChecker implements BugChecker.MethodTreeMatcher {
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String ERROR_MESSAGE =
            "This is a default method annotated with @JsonProperty in an Immutable class, but it hasn't been annotated"
                    + " with @Value.Default. This likely means you forgot to add this annotation. Behaviour when"
                    + " deserializing a string into an instance of this class might fail in unexpected ways during"
                    + " runtime.";

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        if (!belongsToImmutableClass(state)) {
            return Description.NO_MATCH;
        }

        if (!hasDefaultBody(method)) {
            return Description.NO_MATCH;
        }

        if (!ASTHelpers.hasAnnotation(method, JSON_PROPERTY, state)) {
            return Description.NO_MATCH;
        }

        if (hasAnyOfAnnotations(method, state, Set.of(Value.Default.class, Value.Derived.class))) {
            return Description.NO_MATCH;
        }

        return buildDescription(method).setMessage(ERROR_MESSAGE).build();
    }

    private static boolean hasDefaultBody(MethodTree method) {
        return method.getModifiers().getFlags().contains(Modifier.DEFAULT);
    }

    private static boolean hasAnyOfAnnotations(Tree tree, VisitorState state, Set<Class<?>> neededAnnotations) {
        return neededAnnotations.stream()
                .anyMatch(annotation -> ASTHelpers.hasAnnotation(tree, annotation.getName(), state));
    }

    private static boolean belongsToImmutableClass(VisitorState state) {
        ClassTree myClass = getMyClass(state);
        return hasAnyOfAnnotations(myClass, state, Set.of(Value.Immutable.class));
    }

    private static ClassTree getMyClass(VisitorState state) {
        return ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
    }
}
