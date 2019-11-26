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
import com.google.common.collect.ImmutableList;
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
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.Name;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ExceptionSpecificity",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Prefer more specific catch types than Exception and Throwable. When methods are updated to throw "
                + "new checked exceptions they expect callers to handle failure types explicitly. Catching broad "
                + "types defeats the type system.")
public final class ExceptionSpecificity extends BugChecker implements BugChecker.TryTreeMatcher {

    private static final Matcher<Tree> THROWABLE = Matchers.isSameType(Throwable.class);
    private static final Matcher<Tree> EXCEPTION = Matchers.isSameType(Exception.class);

    private static final ImmutableList<String> THROWABLE_REPLACEMENTS =
            ImmutableList.of(RuntimeException.class.getName(), Error.class.getName());
    private static final ImmutableList<String> EXCEPTION_REPLACEMENTS =
            ImmutableList.of(RuntimeException.class.getName());

    @Override
    public Description matchTry(TryTree tree, VisitorState state) {
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
                // Currently we only check that there are no checked exceptions. In a future change
                // we should apply the checked exceptions to our replacement when:
                // 1. Checked exceptions include neither Exception nor Throwable.
                // 2. We have implemented deduplication e.g. [IOException, FileNotFoundException] -> [IOException].
                // 3. There are fewer than some threshold of checked exceptions, perhaps three.
                if (!throwsCheckedExceptions(tree, state)
                        // Do not apply this to test code where it's likely to be noisy.
                        // In the future we may want to revisit this.
                        && !TestCheckUtils.isTestCode(state)) {
                    List<String> replacements = deduplicateCatchTypes(
                            isThrowable ? THROWABLE_REPLACEMENTS : EXCEPTION_REPLACEMENTS,
                            encounteredTypes,
                            state);
                    SuggestedFix.Builder fix = SuggestedFix.builder();
                    if (replacements.isEmpty()) {
                        // If the replacements list is empty, this catch block isn't reachable and can be removed.
                        fix.replace(catchTree, "");
                    } else {
                        ImmutableList<Type> replacementTypes = replacements.stream()
                                .map(state::getTypeFromString)
                                .collect(ImmutableList.toImmutableList());
                        catchTree.accept(new ImpossibleConditionScanner(
                                fix, replacementTypes, catchTree.getParameter().getName()), state);
                        fix.replace(catchTypeTree, replacements.stream()
                                .map(type -> SuggestedFixes.qualifyType(state, fix, type))
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

    /** Caught types cannot be duplicated because code will not compile. */
    private static List<String> deduplicateCatchTypes(
            List<String> proposedReplacements,
            List<Type> caughtTypes,
            VisitorState state) {
        List<String> replacements = new ArrayList<>();
        for (String proposedReplacement : proposedReplacements) {
            Type replacementType = state.getTypeFromString(proposedReplacement);
            if (caughtTypes.stream()
                    .noneMatch(alreadyCaught -> state.getTypes().isSubtype(replacementType, alreadyCaught))) {
                replacements.add(proposedReplacement);
            }
        }
        return replacements;
    }

    private static boolean throwsCheckedExceptions(TryTree tree, VisitorState state) {
        return throwsCheckedExceptions(tree.getBlock(), state)
                || tree.getResources().stream().anyMatch(resource -> resourceThrowsCheckedExceptions(resource, state));
    }

    private static boolean throwsCheckedExceptions(Tree tree, VisitorState state) {
        return MoreASTHelpers.getThrownExceptions(tree, state).stream()
                .anyMatch(type -> MoreASTHelpers.isCheckedException(type, state));
    }

    private static boolean resourceThrowsCheckedExceptions(Tree resource, VisitorState state) {
        if (throwsCheckedExceptions(resource, state)) {
            return true;
        }
        Type resourceType = ASTHelpers.getType(resource);
        if (resourceType == null) {
            return false;
        }
        Symbol.TypeSymbol symbol = resourceType.tsym;
        if (symbol instanceof Symbol.ClassSymbol) {
            return MoreASTHelpers.getCloseMethod((Symbol.ClassSymbol) symbol, state)
                    .map(Symbol.MethodSymbol::getThrownTypes).map(types -> types.stream()
                            .anyMatch(type -> MoreASTHelpers.isCheckedException(type, state)))
                    .orElse(false);
        }
        return false;
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
