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
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * Detects usage of {@code default} clauses in switch statements over Conjure unions (sealed types).
 * <p>
 * When a switch statement includes a {@code default} clause, the Java compiler will not break
 * when new union variants are added. This prevents the compiler from forcing code owners to
 * explicitly acknowledge and handle new types. By using exhaustive switches without {@code default}
 * clauses, the compiler ensures all consumers must consciously decide how to handle new variants
 * before their code compiles again.
 * <p>
 * This warning can and should be suppressed via {@code @SuppressWarnings("ConjureUnionExhaustiveSwitch")}
 * in cases where the consumer explicitly doesn't care about new variants and has a well-defined
 * fallback behavior.
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Avoid using default clause in switch statements on Conjure unions. "
                + "Use exhaustive switch statements instead to ensure all cases are handled explicitly.")
public final class ConjureUnionExhaustiveSwitch extends BugChecker
        implements BugChecker.SwitchTreeMatcher, BugChecker.SwitchExpressionTreeMatcher {

    @Override
    public Description matchSwitch(SwitchTree tree, VisitorState _state) {
        return checkSwitchForSealedType(tree.getExpression(), tree.getCases(), tree);
    }

    @Override
    public Description matchSwitchExpression(SwitchExpressionTree tree, VisitorState _state) {
        return checkSwitchForSealedType(tree.getExpression(), tree.getCases(), tree);
    }

    private Description checkSwitchForSealedType(ExpressionTree expression, List<? extends CaseTree> cases, Tree tree) {
        Type switchType = ASTHelpers.getType(expression);
        if (switchType == null || !isConjureUnion(switchType)) {
            return Description.NO_MATCH;
        }

        if (cases.stream().noneMatch(ASTHelpers::isSwitchDefault)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).build();
    }

    private boolean isConjureUnion(Type type) {
        if (type.asElement() == null
                || type.asElement().getKind() != ElementKind.CLASS
                || !type.asElement().getModifiers().contains(Modifier.SEALED)) {
            return false;
        }

        // Check if it has a nested interface called "Known"
        // Empty conjure unions won't have a Known interface, but they won't be used in switches either by definition
        return ASTHelpers.getEnclosedElements(type.asElement()).stream()
                .anyMatch(element -> element.getKind() == ElementKind.INTERFACE
                        && element.getModifiers().contains(Modifier.SEALED)
                        && "Known".equals(element.getSimpleName().toString()));
    }
}
