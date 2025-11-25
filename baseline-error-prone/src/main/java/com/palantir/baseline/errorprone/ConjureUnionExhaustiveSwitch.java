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
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * Detects unnecessary {@code default} clauses in already-exhaustive switch statements over Conjure unions
 * (sealed types).
 * <p>
 * When a switch statement is already exhaustive (all variants are explicitly handled) but still includes
 * a {@code default} clause, the Java compiler will not break when new union variants are added,
 * preventing the compiler from forcing code owners to explicitly acknowledge and handle new types.
 * <p>
 * By removing the {@code default} clause from exhaustive switches, the compiler helps ensure consumers to
 * consciously decide how to handle new variants before their code compiles again.
 */
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Unnecessary default clause in exhaustive switch statement on Conjure union.")
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

        if (cases.stream().anyMatch(this::hasGuard) || cases.stream().noneMatch(ASTHelpers::isSwitchDefault)) {
            return Description.NO_MATCH;
        }

        if (!(switchType.tsym instanceof ClassSymbol classSymbol)) {
            return Description.NO_MATCH;
        }

        Optional<List<Type>> permittedSubtypes = Optional.ofNullable(classSymbol.getPermittedSubclasses());
        if (permittedSubtypes.isEmpty()) {
            return Description.NO_MATCH;
        }
        Set<Type> permittedTypeSet = new HashSet<>(permittedSubtypes.get());

        Set<Type> handledTypes = new HashSet<>();
        for (CaseTree caseTree : cases) {
            if (ASTHelpers.isSwitchDefault(caseTree)) {
                continue;
            }

            for (Tree label : getLabels(caseTree)) {
                extractType(label).ifPresent(type -> addHandledType(type, handledTypes));
            }
        }

        // If all permitted types are explicitly handled, the switch is exhaustive and the default is unnecessary
        if (handledTypes.containsAll(permittedTypeSet)) {
            return buildDescription(tree).build();
        }

        return Description.NO_MATCH;
    }

    private void addHandledType(Type type, Set<Type> handledTypes) {
        if (type.tsym instanceof ClassSymbol classSymbol) {
            Optional<List<Type>> permitted = Optional.ofNullable(classSymbol.getPermittedSubclasses());
            if (permitted.isPresent()) {
                // Handle matching on `Known` type
                handledTypes.addAll(permitted.get());
                return;
            }
        }

        // Default case
        handledTypes.add(type);
    }

    private boolean hasGuard(CaseTree caseTree) {
        try {
            Method getGuardMethod = CaseTree.class.getMethod("getGuard");
            return getGuardMethod.invoke(caseTree) != null;
        } catch (ReflectiveOperationException _e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<? extends Tree> getLabels(CaseTree caseTree) {
        try {
            // `getLabels` is a preview feature - annotation indicates we should use reflection to invoke
            Method getLabelsMethod = CaseTree.class.getMethod("getLabels");
            return (List<? extends Tree>) getLabelsMethod.invoke(caseTree);
        } catch (ReflectiveOperationException _e) {
            return Collections.emptyList();
        }
    }

    private Optional<Type> extractType(Tree label) {
        try {
            // Always `CaseLabelTree` (preview type) for sealed type
            Method getPatternMethod = label.getClass().getMethod("getPattern");
            Tree pattern = (Tree) getPatternMethod.invoke(label);
            if (pattern != null) {
                return Optional.ofNullable(ASTHelpers.getType(pattern));
            }
        } catch (ReflectiveOperationException _e) {
            // Ignore
        }
        return Optional.empty();
    }

    private boolean isConjureUnion(Type type) {
        if (type.asElement() == null || !type.asElement().getModifiers().contains(Modifier.SEALED)) {
            return false;
        }

        return isUnionClass(type.asElement())
                || (isKnownInterface(type.asElement()) && isEnclosedBySealedClass(type.asElement()));
    }

    private boolean isUnionClass(javax.lang.model.element.Element classElement) {
        return classElement.getKind() == ElementKind.CLASS
                && ASTHelpers.getEnclosedElements((com.sun.tools.javac.code.Symbol) classElement).stream()
                        .anyMatch(this::isKnownInterface);
    }

    private boolean isKnownInterface(javax.lang.model.element.Element element) {
        return element.getKind() == ElementKind.INTERFACE
                && element.getModifiers().contains(Modifier.SEALED)
                && "Known".equals(element.getSimpleName().toString());
    }

    private boolean isEnclosedBySealedClass(javax.lang.model.element.Element element) {
        return element.getEnclosingElement() != null
                && element.getEnclosingElement().getKind() == ElementKind.CLASS
                && element.getEnclosingElement().getModifiers().contains(Modifier.SEALED);
    }
}
