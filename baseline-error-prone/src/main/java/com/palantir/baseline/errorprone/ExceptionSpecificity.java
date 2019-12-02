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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ExceptionSpecificity",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Prefer more specific error types than Exception and Throwable. When methods are updated to throw "
                + "new checked exceptions they expect callers to handle failure types explicitly. Catching broad "
                + "types defeats the type system. By catching the most specific types possible we leverage existing "
                + "compiler functionality to detect unreachable code.")
public final class ExceptionSpecificity
        extends BugChecker implements BugChecker.MethodTreeMatcher, BugChecker.TryTreeMatcher {

    private static boolean MATCH_TRY = Boolean.getBoolean("ExceptionSpecificity.try");
    // Maximum of three checked exception types to avoid unreadable long catch statements.
    private static final int MAX_CHECKED_EXCEPTIONS = 3;
    private static final Matcher<Tree> THROWABLE = Matchers.isSameType(Throwable.class);
    private static final Matcher<Tree> EXCEPTION = Matchers.isSameType(Exception.class);

    private static final ImmutableList<String> THROWABLE_REPLACEMENTS =
            ImmutableList.of(RuntimeException.class.getName(), Error.class.getName());
    private static final ImmutableList<String> EXCEPTION_REPLACEMENTS =
            ImmutableList.of(RuntimeException.class.getName());

    @Override
    @SuppressWarnings("CyclomaticComplexity")
    public Description matchTry(TryTree tree, VisitorState state) {
        if (!MATCH_TRY) {
            return Description.NO_MATCH;
        }
        List<Type> encounteredTypes = new ArrayList<>();
        for (CatchTree catchTree : tree.getCatches()) {
            Tree catchTypeTree = catchTree.getParameter().getType();
            Type catchType = ASTHelpers.getType(catchTypeTree);
            // Don't match union types for now e.g. 'catch (RuntimeException | Error e)'
            // It's not worth the complexity at this point.
            if (catchType == null) {
                continue;
            }
            if (catchType.isUnion()) {
                encounteredTypes.addAll(MoreASTHelpers.expandUnion(catchType));
                continue;
            }

            boolean isException = EXCEPTION.matches(catchTypeTree, state);
            boolean isThrowable = THROWABLE.matches(catchTypeTree, state);
            if (isException || isThrowable) {
                // In a future change we may want to support flattening exceptions with common ancestors
                // e.g. [ConnectException, FileNotFoundException, SocketException] -> [IOException].
                ImmutableList<Type> thrownCheckedExceptions = normalizeExceptions(
                        getThrownCheckedExceptions(tree, state), state);
                if (containsBroadException(thrownCheckedExceptions, state)) {
                    return Description.NO_MATCH;
                }
                ImmutableList<Type> thrown = flattenExceptionTypes(thrownCheckedExceptions, state);
                if (thrown.size() <= MAX_CHECKED_EXCEPTIONS
                        // Do not apply this to test code where it's likely to be noisy.
                        // In the future we may want to revisit this.
                        && !TestCheckUtils.isTestCode(state)) {
                    List<Type> replacements = deduplicateCatchTypes(
                            ImmutableList.<Type>builder()
                                    .addAll(thrown)
                                    .addAll((isThrowable ? THROWABLE_REPLACEMENTS : EXCEPTION_REPLACEMENTS).stream()
                                            .map(name -> Preconditions.checkNotNull(
                                                    state.getTypeFromString(name), "Failed to find type"))
                                            .collect(ImmutableList.toImmutableList()))
                                    .build(),
                            encounteredTypes,
                            state);
                    SuggestedFix.Builder fix = SuggestedFix.builder();
                    if (replacements.isEmpty()) {
                        // If the replacements list is empty, this catch block isn't reachable and can be removed.
                        fix.replace(catchTree, "");
                    } else {
                        catchTree.accept(new ImpossibleConditionScanner(
                                fix, replacements, catchTree.getParameter().getName()), state);
                        fix.replace(catchTypeTree, replacements.stream()
                                .map(type -> SuggestedFixes.prettyType(state, fix, type))
                                .sorted()
                                .collect(Collectors.joining(" | ")));
                    }
                    return buildDescription(catchTypeTree)
                            .addFix(fix.build())
                            .build();
                }
                return Description.NO_MATCH;
            }
            // mark the type as caught before continuing
            encounteredTypes.add(catchType);
        }
        return Description.NO_MATCH;
    }

    @Override
    @SuppressWarnings("CyclomaticComplexity")
    public Description matchMethod(MethodTree tree, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null) {
            return Description.NO_MATCH;
        }

        Set<Modifier> methodModifiers = symbol.getModifiers();
        if (methodModifiers.contains(Modifier.ABSTRACT)
                // Don't suggest modifying public API
                || methodModifiers.contains(Modifier.PUBLIC)) {
            return Description.NO_MATCH;
        }
        List<? extends ExpressionTree> throwsExpressions = tree.getThrows();
        if (throwsExpressions.size() != 1) {
            return Description.NO_MATCH;
        }
        Types types = state.getTypes();
        if (ASTHelpers.findSuperMethod(ASTHelpers.getSymbol(tree), types).isPresent()) {
            return Description.NO_MATCH;
        }
        Type exception = state.getTypeFromString(Exception.class.getName());
        Type throwable = state.getTypeFromString(Throwable.class.getName());
        ExpressionTree throwsExpression = Iterables.getOnlyElement(throwsExpressions);
        Type throwsExpressionType = ASTHelpers.getType(throwsExpression);
        if (throwsExpressionType != null
                && (ASTHelpers.isSameType(throwsExpressionType, exception, state)
                || ASTHelpers.isSameType(throwsExpressionType, throwable, state))) {
            ImmutableSet<Type> allThrownExceptions = MoreASTHelpers.getThrownExceptions(tree.getBody(), state);
            ImmutableList<Type> normalizedThrownExceptions = allThrownExceptions.stream()
                    .filter(type -> MoreASTHelpers.isCheckedException(type, state))
                    .map(type -> normalizeAnonymousType(type, state))
                    .collect(ImmutableList.toImmutableList());
            ImmutableList<Type> checkedExceptions = flattenExceptionTypes(normalizedThrownExceptions, state);
            if (checkedExceptions.size() > MAX_CHECKED_EXCEPTIONS
                    || containsBroadException(checkedExceptions, state)
                    // Avoid code churn in test sources for the time being.
                    || TestCheckUtils.isTestCode(state)) {
                return Description.NO_MATCH;
            }
            if (checkedExceptions.isEmpty()) {
                return buildDescription(throwsExpression)
                        .addFix(SuggestedFixes.deleteExceptions(tree, state, ImmutableList.of(throwsExpression)))
                        .build();
            }
            SuggestedFix.Builder fix = SuggestedFix.builder();
            return buildDescription(throwsExpression)
                    .addFix(fix.replace(throwsExpression, checkedExceptions.stream()
                            .map(checkedException -> SuggestedFixes.prettyType(state, fix, checkedException))
                            .sorted()
                            .collect(Collectors.joining(", ")))
                            .build())
                    .build();

        }
        return Description.NO_MATCH;
    }

    /** Caught types cannot be duplicated because code will not compile. */
    private static List<Type> deduplicateCatchTypes(
            List<Type> proposedReplacements,
            List<Type> caughtTypes,
            VisitorState state) {
        List<Type> replacements = new ArrayList<>();
        for (Type replacementType : proposedReplacements) {
            if (caughtTypes.stream()
                    .noneMatch(alreadyCaught -> state.getTypes().isSubtype(replacementType, alreadyCaught))) {
                replacements.add(replacementType);
            }
        }
        return replacements;
    }

    private static ImmutableList<Type> getThrownCheckedExceptions(TryTree tree, VisitorState state) {
        return MoreASTHelpers.getThrownExceptionsFromTryBody(tree, state).stream()
                .filter(type -> MoreASTHelpers.isCheckedException(type, state))
                .collect(ImmutableList.toImmutableList());
    }

    private static boolean containsBroadException(Collection<Type> exceptions, VisitorState state) {
        return exceptions.stream().anyMatch(type ->
                ASTHelpers.isSameType(state.getTypeFromString(Exception.class.getName()), type, state)
                        || ASTHelpers.isSameType(state.getTypeFromString(Throwable.class.getName()), type, state));
    }

    /** Removes any exception type that is a subtype of another type in the set. */
    private static ImmutableList<Type> normalizeExceptions(ImmutableList<Type> types, VisitorState state) {
        return types.stream()
                .map(type -> normalizeAnonymousType(type, state))
                .collect(ImmutableList.toImmutableList());
    }

    /** Anonymous types cannot be referenced directly, so we must use the supertype. */
    private static Type normalizeAnonymousType(Type input, VisitorState state) {
        Type upperBound = input.getUpperBound();
        if (upperBound != null) {
            return normalizeAnonymousType(upperBound, state);
        }
        if (input.tsym.isAnonymous()) {
            return normalizeAnonymousType(state.getTypes().supertype(input), state);
        }
        return input;
    }

    /** Removes any exception type that is a subtype of another type in the set. */
    private static ImmutableList<Type> flattenExceptionTypes(ImmutableList<Type> types, VisitorState state) {
        ImmutableList.Builder<Type> deduplicatedBuilder = ImmutableList.builderWithExpectedSize(types.size());
        for (int i = 0; i < types.size(); i++) {
            Type current = types.get(i);
            boolean duplicate = false;
            for (int j = 0; j < i; j++) {
                if (ASTHelpers.isSameType(types.get(j), current, state)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduplicatedBuilder.add(current);
            }
        }
        ImmutableList<Type> deduplicated = deduplicatedBuilder.build();
        return deduplicated.stream()
                .filter(type -> deduplicated.stream().noneMatch(item -> !Objects.equals(type, item)
                        && state.getTypes().isSubtype(type, item)))
                .collect(ImmutableList.toImmutableList());
    }

    private static final class ImpossibleConditionScanner extends TreeScanner<Void, VisitorState> {

        private final SuggestedFix.Builder fix;
        private final List<Type> caughtTypes;
        private final Name exceptionName;

        ImpossibleConditionScanner(
                SuggestedFix.Builder fix,
                List<Type> caughtTypes,
                Name exceptionName) {
            this.fix = fix;
            this.caughtTypes = caughtTypes;
            this.exceptionName = exceptionName;
        }

        @Override
        public Void visitIf(IfTree node, VisitorState state) {
            return node.getCondition().accept(new SimpleTreeVisitor<Void, Void>() {
                @Override
                public Void visitInstanceOf(InstanceOfTree instanceOfNode, Void ignored) {
                    if (!matchesInstanceOf(instanceOfNode, state)) {
                        return null;
                    }
                    if (node.getElseStatement() == null) {
                        fix.replace(node, "");
                    } else {
                        fix.replace(node, unwrapBlock(node.getElseStatement(), state));
                    }
                    return null;
                }

                @Override
                public Void visitParenthesized(ParenthesizedTree node, Void ignored) {
                    return node.getExpression().accept(this, ignored);
                }
            }, null);
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree node, VisitorState state) {
            if (matchesInstanceOf(node, state)) {
                fix.replace(node, "false");
            }
            return null;
        }

        private boolean matchesInstanceOf(InstanceOfTree instanceOfNode, VisitorState state) {
            ExpressionTree expression = instanceOfNode.getExpression();
            return expression instanceof IdentifierTree
                    && ((IdentifierTree) expression).getName().contentEquals(exceptionName)
                    && !isTypeValid(ASTHelpers.getType(instanceOfNode.getType()), state);
        }

        // Avoid searching outside the current scope
        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, VisitorState state) {
            return null;
        }

        // Avoid searching outside the current scope
        @Override
        public Void visitNewClass(NewClassTree var1, VisitorState state) {
            return null;
        }

        private boolean isTypeValid(Type instanceOfTarget, VisitorState state) {
            return caughtTypes.stream().anyMatch(caught -> state.getTypes().isCastable(caught, instanceOfTarget));
        }

        @Nullable
        private static String unwrapBlock(StatementTree statement, VisitorState state) {
            if (statement.getKind() == Tree.Kind.BLOCK) {
                CharSequence source = state.getSourceCode();
                if (source == null) {
                    return null;
                }
                BlockTree blockStatement = (BlockTree) statement;
                List<? extends StatementTree> statements = blockStatement.getStatements();
                if (statements.isEmpty()) {
                    return "";
                }
                int startPosition = ((JCTree) statements.get(0)).getStartPosition();
                int endPosition = state.getEndPosition(statements.get(statements.size() - 1));
                return source.subSequence(startPosition, endPosition).toString();
            }
            return state.getSourceForNode(statement);
        }
    }
}
