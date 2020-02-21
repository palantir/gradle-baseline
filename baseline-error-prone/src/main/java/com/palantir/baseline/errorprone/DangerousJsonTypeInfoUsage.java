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
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.AnnotationHasArgumentWithValue;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.IsSameType;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ExpressionTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousJsonTypeInfoUsage",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Disallow usage of Jackson's JsonTypeInfo.Id.CLASS annotation for security reasons, "
                + "cf. https://github.com/FasterXML/jackson-databind/issues/1599")
public final class DangerousJsonTypeInfoUsage extends BugChecker implements BugChecker.AnnotationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<AnnotationTree> matcher = new AnnotationHasArgumentWithValue(
            "use",
            Matchers.allOf(
                    new IsSameType<>("com.fasterxml.jackson.annotation.JsonTypeInfo$Id"),
                    Matchers.anyOf(
                            treeEqualsStringMatcher("JsonTypeInfo.Id.CLASS"),
                            treeEqualsStringMatcher("JsonTypeInfo.Id.MINIMAL_CLASS"),
                            treeEqualsStringMatcher("com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS"),
                            treeEqualsStringMatcher(
                                    "com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS"))));

    private static Matcher<ExpressionTree> treeEqualsStringMatcher(String value) {
        return (expressionTree, state) -> state.getSourceForNode(expressionTree).equals(value);
    }

    @Override
    public Description matchAnnotation(AnnotationTree tree, VisitorState state) {
        if (!matcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Must not use Jackson @JsonTypeInfo annotation with "
                        + "JsonTypeInfo.Id.CLASS or JsonTypeInfo.Id.MINIMAL_CLASS")
                .build();
    }
}
