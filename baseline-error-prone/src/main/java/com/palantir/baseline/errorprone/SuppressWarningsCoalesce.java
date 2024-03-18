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
import com.sun.source.tree.MethodTree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "blah")
public final class SuppressWarningsCoalesce extends BugChecker implements BugChecker.MethodTreeMatcher {
    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        return Description.NO_MATCH;

        //        List<AnnotationTree> suppressWarnings = tree.getModifiers().getAnnotations().stream()
        //                .filter(annotation -> ((IdentifierTree) annotation.getAnnotationType())
        //                        .getName()
        //                        .contentEquals("SuppressWarnings"))
        //                .collect(Collectors.toList());
        //
        //        if (suppressWarnings.size() <= 1) {
        //            return Description.NO_MATCH;
        //        }
        //
        //        List<? extends ExpressionTree> collect = suppressWarnings.stream()
        //                .flatMap(annotationTree -> annotationTree.getArguments().stream())
        //                .collect(Collectors.toList());
        //
        //        return Description.NO_MATCH;
    }
}
