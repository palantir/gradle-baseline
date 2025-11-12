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
import com.sun.source.tree.InstanceOfTree;
import com.sun.tools.javac.code.Type;

/**
 * Detects usage of {@code instanceof} checks on Conjure unions.
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Avoid using instanceof checks on Conjure unions. "
                + "Use switch expressions/visitors instead to ensure all variants are handled explicitly.")
public final class ConjureUnionInstanceof extends BugChecker implements BugChecker.InstanceOfTreeMatcher {

    @Override
    public Description matchInstanceOf(InstanceOfTree tree, VisitorState _state) {
        Type expressionType = ASTHelpers.getType(tree.getExpression());
        if (expressionType == null || !ConjureUtils.isConjureUnion(expressionType)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).build();
    }
}
