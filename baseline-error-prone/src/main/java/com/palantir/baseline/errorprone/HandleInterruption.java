/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.ErrorProneTokens;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;

@AutoService(BugChecker.class)
@BugPattern(
        name = "HandleInterruption",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "InterruptedException must be handled by either rethrowing the InterruptedException or setting "
                + "Thread.currentThread().interrupt(). Failure to do so can result in retrial of long-running "
                + "operations that are expected to be terminated.")
public final class HandleInterruption extends BugChecker implements BugChecker.TryTreeMatcher {

    private static final Matcher<ExpressionTree> THREAD_INTERRUPT = MethodMatchers.instanceMethod()
            .onDescendantOf(Thread.class.getName())
            .named("interrupt")
            .withParameters();

    private static final Matcher<Tree> CONTAINS_INTERRUPT =
            Matchers.contains(ExpressionTree.class, THREAD_INTERRUPT);
    private static final Matcher<Tree> CONTAINS_INSTANCEOF_INTERRUPTED_EXCEPTION = Matchers.contains(
            InstanceOfTree.class,
            (Matcher<InstanceOfTree>) (instanceOfTree, state) -> ASTHelpers.isSubtype(
                    ASTHelpers.getType(instanceOfTree.getType()),
                    state.getTypeFromString(InterruptedException.class.getName()),
                    state));

    @Override
    public Description matchTry(TryTree tree, VisitorState state) {
        for (CatchTree catchTree : tree.getCatches()) {
            Optional<InterruptedCatchType> maybeCatchType = getCatchType(
                    ASTHelpers.getType(catchTree.getParameter().getType()), state);
            if (!maybeCatchType.isPresent()) {
                return Description.NO_MATCH;
            }
            // Check catch blocks first, they're less expensive to search.
            InterruptedCatchType catchType = maybeCatchType.get();
            if (!catchType.matches()) {
                continue;
            }
            if (doesNotMatchCatchTree(tree, catchTree, state)) {
                return Description.NO_MATCH;
            }
            BlockTree blockTree = catchTree.getBlock();
            // Only match blocks which throw an exception as the last parameter, assuming that
            // exceptions are handled without rethrowing should reset interruption as well.
            List<? extends StatementTree> statements = blockTree.getStatements();
            if (!statements.isEmpty() && statements.get(statements.size() - 1) instanceof ThrowTree) {
                return buildDescription(catchTree)
                        .addFix(SuggestedFix.builder()
                                .prefixWith(statements.get(0), catchType.getFix(catchTree))
                                .build())
                        .build();
            }
            return Description.NO_MATCH;
        }
        return Description.NO_MATCH;
    }

    private static boolean doesNotMatchCatchTree(TryTree tryTree, CatchTree catchTree, VisitorState state) {
        return !throwsInterruptedException(tryTree, state)
                || containsSuppressionComment(catchTree, state)
                || CONTAINS_INTERRUPT.matches(catchTree, state)
                // These checks aren't always correct, but the goal is to avoid creating noise,
                // not necessarily to
                || CONTAINS_INSTANCEOF_INTERRUPTED_EXCEPTION.matches(catchTree, state)
                || containsRethrow(catchTree, state)
                // Avoid excessive noise in test code. While there's an argument for including
                // this check in test code, it makes the rollout significantly noisier and can
                // be included later.
                || TestCheckUtils.isTestCode(state);
    }

    private static boolean containsSuppressionComment(CatchTree catchTree, VisitorState state) {
        return ErrorProneTokens.getTokens(state.getSourceForNode(catchTree), state.context).stream()
                .anyMatch(errorProneToken -> errorProneToken.comments().stream()
                        .anyMatch(comment -> comment.getText().contains("interruption reset")));
    }

    /**
     * Returns true if the TryTree body can throw an exception that could be an InterruptedException.
     * This may be an exact match, subtype, or a supertype like {@link Exception}.
     */
    private static boolean throwsInterruptedException(TryTree tryTree, VisitorState state) {
        return MoreASTHelpers.getThrownExceptionsFromTryBody(tryTree, state).stream()
                .anyMatch(type -> ASTHelpers.isCastable(
                        type, state.getTypeFromString(InterruptedException.class.getName()), state));
    }

    /** Returns true if the catch tree contains a statement which rethrows the caught exception. */
    private static boolean containsRethrow(CatchTree catchTree, VisitorState state) {
        return Matchers.contains(ThrowTree.class, (Matcher<ThrowTree>) (throwTree, _state) ->
                throwTree.getExpression() instanceof IdentifierTree
                        && ((IdentifierTree) throwTree.getExpression()).getName()
                        .contentEquals(catchTree.getParameter().getName()))
                .matches(catchTree.getBlock(), state);
    }

    private static Optional<InterruptedCatchType> getCatchType(Type type, VisitorState state) {
        Type interruptedException = state.getTypeFromString(InterruptedException.class.getName());
        if (state.getTypes().isAssignable(type, interruptedException)) {
            return Optional.of(InterruptedCatchType.INTERRUPTED_SUBTYPE);
        }
        if (state.getTypes().isAssignable(interruptedException, type)) {
            if (type instanceof UnionType) {
                List<? extends TypeMirror> typeMirrors = ((UnionType) type).getAlternatives();
                if (!typeMirrors.stream().allMatch(Type.class::isInstance)) {
                    return Optional.empty();
                }
                List<Optional<InterruptedCatchType>> interruptedCatchTypes = typeMirrors.stream()
                        .map(Type.class::cast)
                        .map(unionType -> getCatchType(unionType, state))
                        .collect(ImmutableList.toImmutableList());
                if (!interruptedCatchTypes.stream().allMatch(Optional::isPresent)) {
                    return Optional.empty();
                }
                Set<InterruptedCatchType> uniqueTypes = interruptedCatchTypes.stream()
                        .map(Optional::get)
                        .collect(ImmutableSet.toImmutableSet());
                if (uniqueTypes.size() == 1) {
                    return Optional.of(Iterables.getOnlyElement(uniqueTypes));
                }
                return Optional.of(InterruptedCatchType.INTERRUPTED_SUPERTYPE);
            }
            return Optional.of(InterruptedCatchType.INTERRUPTED_SUPERTYPE);
        }
        return Optional.of(InterruptedCatchType.DOES_NOT_MATCH);
    }

    enum InterruptedCatchType {
        DOES_NOT_MATCH,
        /** Includes subtypes. */
        INTERRUPTED_SUBTYPE,
        /** Catching Exception will consume an InterruptedException. */
        INTERRUPTED_SUPERTYPE;

        boolean matches() {
            return this != DOES_NOT_MATCH;
        }

        String getFix(CatchTree catchTree) {
            switch (this) {
                case DOES_NOT_MATCH:
                    throw new IllegalStateException("Cannot fix code that does not match");
                case INTERRUPTED_SUBTYPE:
                    return "Thread.currentThread().interrupt();\n";
                case INTERRUPTED_SUPERTYPE:
                    return "if (" + catchTree.getParameter().getName().toString()
                            + " instanceof InterruptedException) { Thread.currentThread().interrupt(); }\n";
            }
            throw new IllegalStateException("Unknown InterruptedCatchType " + this);
        }
    }
}
