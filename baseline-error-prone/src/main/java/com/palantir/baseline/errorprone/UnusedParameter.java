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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.*;
import static com.google.errorprone.util.SideEffectAnalysis.hasSideEffect;
import static com.sun.source.tree.Tree.Kind.*;

import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Position;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;

/** Bugpattern to detect unused declarations. */
@AutoService(BugChecker.class)
@BugPattern(
        name = "UnusedParameter",
        altNames = {"unused", "UnusedParameters"},
        summary = "Unused.",
        providesFix = REQUIRES_HUMAN_ATTENTION,
        severity = ERROR,
        documentSuppression = false)
public final class UnusedParameter extends BugChecker implements BugChecker.CompilationUnitTreeMatcher {
    private static final String EXEMPT_PREFIX = "unused";

    /**
     * The set of annotation full names which exempt annotated element from being reported as unused.
     */
    private static final ImmutableSet<String> EXEMPTING_VARIABLE_ANNOTATIONS =
            ImmutableSet.of(
                    "javax.persistence.Basic",
                    "javax.persistence.Column",
                    "javax.persistence.Id",
                    "javax.persistence.Version",
                    "javax.xml.bind.annotation.XmlElement",
                    "org.junit.Rule",
                    "org.mockito.Mock",
                    "org.openqa.selenium.support.FindBy",
                    "org.openqa.selenium.support.FindBys");

    /** The set of types exempting a type that is extending or implementing them. */
    private static final ImmutableSet<String> EXEMPTING_SUPER_TYPES =
            ImmutableSet.of(
            );

    /** The set of types exempting a field of type extending them. */
    private static final ImmutableSet<String> EXEMPTING_FIELD_SUPER_TYPES =
            ImmutableSet.of("org.junit.rules.TestRule");

    private static final ImmutableList<String> SPECIAL_FIELDS =
            ImmutableList.of(
                    "serialVersionUID",
                    // TAG fields are used by convention in Android apps.
                    "TAG");

    private static final ImmutableSet<Modifier> LOGGER_REQUIRED_MODIFIERS =
            Sets.immutableEnumSet(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

    private static final ImmutableSet<String> LOGGER_TYPE_NAME = ImmutableSet.of("GoogleLogger");

    private static final ImmutableSet<String> LOGGER_VAR_NAME = ImmutableSet.of("logger");
    private final boolean reportInjectedFields;

    public UnusedParameter(ErrorProneFlags flags) {
        ImmutableSet.Builder<String> methodAnnotationsExemptingParameters =
                ImmutableSet.<String>builder()
                        .add("org.robolectric.annotation.Implementation");
        flags
                .getList("Unused:methodAnnotationsExemptingParameters")
                .ifPresent(methodAnnotationsExemptingParameters::addAll);
        this.reportInjectedFields = flags.getBoolean("Unused:ReportInjectedFields").orElse(false);
    }

    @Override
    public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
        // We will skip reporting on the whole compilation if there are any native methods found.
        // Use a TreeScanner to find all local variables and fields.
        if (hasNativeMethods(tree)) {
            return Description.NO_MATCH;
        }

        VariableFinder variableFinder = new VariableFinder(state);
        variableFinder.scan(state.getPath(), null);

        // Map of symbols to variable declarations. Initially this is a map of all of the local variable
        // and fields. As we go we remove those variables which are used.
        Map<Symbol, TreePath> unusedElements = variableFinder.unusedElements;

        // Map of symbols to their usage sites. In this map we also include the definition site in
        // addition to all the trees where symbol is used. This map is designed to keep the usage sites
        // of variables (parameters, fields, locals).
        //
        // We populate this map when analyzing the unused variables and then use it to generate
        // appropriate fixes for them.
        ListMultimap<Symbol, TreePath> usageSites = variableFinder.usageSites;

        FilterUsedVariables
                filterUsedVariables = new FilterUsedVariables(unusedElements, usageSites);
        filterUsedVariables.scan(state.getPath(), null);

        // Keeps track of whether a symbol was _ever_ used (between reassignments).
        Set<Symbol> isEverUsed = filterUsedVariables.isEverUsed;
        List<UnusedSpec> unusedSpecs = filterUsedVariables.unusedSpecs;

        // Add the left-over unused variables...
        for (Map.Entry<Symbol, TreePath> entry : unusedElements.entrySet()) {
            unusedSpecs.add(
                    UnusedSpec.of(entry.getKey(), entry.getValue(), usageSites.get(entry.getKey()), null));
        }

        ImmutableListMultimap<Symbol, UnusedSpec> unusedSpecsBySymbol =
                Multimaps.index(unusedSpecs, UnusedSpec::symbol);

        for (Map.Entry<Symbol, Collection<UnusedSpec>> entry : unusedSpecsBySymbol.asMap().entrySet()) {
            Symbol unusedSymbol = entry.getKey();
            Collection<UnusedSpec> specs = entry.getValue();

            ImmutableList<TreePath> allUsageSites =
                    specs.stream().flatMap(u -> u.usageSites().stream()).collect(toImmutableList());
            if (!unusedElements.containsKey(unusedSymbol)) {
                isEverUsed.add(unusedSymbol);
            }
            SuggestedFix makeFirstAssignmentDeclaration =
                    makeAssignmentDeclaration(unusedSymbol, specs, allUsageSites, state);
            Tree unused = specs.iterator().next().variableTree().getLeaf();
            Symbol.VarSymbol symbol = (Symbol.VarSymbol) unusedSymbol;
            ImmutableList<SuggestedFix> fixes;
            if (symbol.getKind() == ElementKind.PARAMETER
                    && !isEverUsed.contains(unusedSymbol)) {
                fixes = buildUnusedParameterFixes(symbol, allUsageSites, state);
            } else {
                fixes = buildUnusedVarFixes(symbol, allUsageSites, state);
            }
            state.reportMatch(
                    buildDescription(unused)
                            .setMessage(
                                    String.format(
                                            "%s %s '%s' is never read.",
                                            unused instanceof VariableTree ? "The" : "The assignment to this",
                                            describeVariable(symbol),
                                            symbol.name))
                            .addAllFixes(
                                    fixes.stream()
                                            .map(
                                                    f ->
                                                            SuggestedFix.builder()
                                                                    .merge(makeFirstAssignmentDeclaration)
                                                                    .merge(f)
                                                                    .build())
                                            .collect(toImmutableList()))
                            .build());
        }
        return Description.NO_MATCH;
    }

    private static SuggestedFix makeAssignmentDeclaration(
            Symbol unusedSymbol,
            Collection<UnusedSpec> specs,
            ImmutableList<TreePath> allUsageSites,
            VisitorState state) {
        if (unusedSymbol.getKind() != ElementKind.LOCAL_VARIABLE) {
            return SuggestedFix.builder().build();
        }
        Optional<VariableTree> removedVariableTree =
                allUsageSites.stream()
                        .filter(tp -> tp.getLeaf() instanceof VariableTree)
                        .findFirst()
                        .map(tp -> (VariableTree) tp.getLeaf());
        Optional<AssignmentTree> reassignment =
                specs.stream()
                        .map(UnusedSpec::terminatingAssignment)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(a -> allUsageSites.stream().noneMatch(tp -> tp.getLeaf().equals(a)))
                        .findFirst();
        if (!removedVariableTree.isPresent() || !reassignment.isPresent()) {
            return SuggestedFix.builder().build();
        }
        return SuggestedFix.prefixWith(
                reassignment.get(), state.getSourceForNode(removedVariableTree.get().getType()) + " ");
    }

    private static String describeVariable(Symbol.VarSymbol symbol) {
        switch (symbol.getKind()) {
            case FIELD:
                return "field";
            case LOCAL_VARIABLE:
                return "local variable";
            case PARAMETER:
                return "parameter";
            default:
                return "variable";
        }
    }

    private static boolean hasNativeMethods(CompilationUnitTree tree) {
        AtomicBoolean hasAnyNativeMethods = new AtomicBoolean(false);
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree tree, Void unused) {
                if (tree.getModifiers().getFlags().contains(Modifier.NATIVE)) {
                    hasAnyNativeMethods.set(true);
                }
                return null;
            }
        }.scan(tree, null);
        return hasAnyNativeMethods.get();
    }

    // https://docs.oracle.com/javase/specs/jls/se11/html/jls-14.html#jls-ExpressionStatement
    private static final ImmutableSet<Tree.Kind> TOP_LEVEL_EXPRESSIONS =
            ImmutableSet.of(
                    Tree.Kind.ASSIGNMENT,
                    Tree.Kind.PREFIX_INCREMENT,
                    Tree.Kind.PREFIX_DECREMENT,
                    Tree.Kind.POSTFIX_INCREMENT,
                    Tree.Kind.POSTFIX_DECREMENT,
                    Tree.Kind.METHOD_INVOCATION,
                    Tree.Kind.NEW_CLASS);

    private static boolean needsBlock(TreePath path) {
        Tree leaf = path.getLeaf();
        class Visitor extends SimpleTreeVisitor<Boolean, Void> {

            @Override
            public Boolean visitIf(IfTree tree, Void unused) {
                return tree.getThenStatement() == leaf || tree.getElseStatement() == leaf;
            }

            @Override
            public Boolean visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
                return tree.getStatement() == leaf;
            }

            @Override
            public Boolean visitWhileLoop(WhileLoopTree tree, Void unused) {
                return tree.getStatement() == leaf;
            }

            @Override
            public Boolean visitForLoop(ForLoopTree tree, Void unused) {
                return tree.getStatement() == leaf;
            }

            @Override
            public Boolean visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
                return tree.getStatement() == leaf;
            }
        }
        return firstNonNull(path.getParentPath().getLeaf().accept(new Visitor(), null), false);
    }

    private static ImmutableList<SuggestedFix> buildUnusedVarFixes(
            Symbol varSymbol, List<TreePath> usagePaths, VisitorState state) {
        // Don't suggest a fix for fields annotated @Inject: we can warn on them, but they *could* be
        // used outside the class.
        if (ASTHelpers.hasDirectAnnotationWithSimpleName(varSymbol, "Inject")) {
            return ImmutableList.of();
        }
        ElementKind varKind = varSymbol.getKind();
        boolean encounteredSideEffects = false;
        SuggestedFix.Builder fix = SuggestedFix.builder().setShortDescription("remove unused variable");
        SuggestedFix.Builder removeSideEffectsFix =
                SuggestedFix.builder().setShortDescription("remove unused variable and any side effects");
        for (TreePath usagePath : usagePaths) {
            StatementTree statement = (StatementTree) usagePath.getLeaf();
            if (statement.getKind() == Tree.Kind.VARIABLE) {
                if (getSymbol(statement).getKind() == ElementKind.PARAMETER) {
                    continue;
                }
                VariableTree variableTree = (VariableTree) statement;
                ExpressionTree initializer = variableTree.getInitializer();
                if (hasSideEffect(initializer) && TOP_LEVEL_EXPRESSIONS.contains(initializer.getKind())) {
                    encounteredSideEffects = true;
                    if (varKind == ElementKind.FIELD) {
                        String newContent =
                                String.format(
                                        "%s{ %s; }",
                                        varSymbol.isStatic() ? "static " : "", state.getSourceForNode(initializer));
                        fix.merge(SuggestedFixes.replaceIncludingComments(usagePath, newContent, state));
                        removeSideEffectsFix.replace(statement, "");
                    } else {
                        fix.replace(statement, String.format("%s;", state.getSourceForNode(initializer)));
                        removeSideEffectsFix.replace(statement, "");
                    }
                } else if (isEnhancedForLoopVar(usagePath)) {
                    String modifiers =
                            nullToEmpty(
                                    variableTree.getModifiers() == null
                                            ? null
                                            : state.getSourceForNode(variableTree.getModifiers()));
                    String newContent =
                            String.format(
                                    "%s%s unused",
                                    modifiers.isEmpty() ? "" : (modifiers + " "), variableTree.getType());
                    // The new content for the second fix should be identical to the content for the first
                    // fix in this case because we can't just remove the enhanced for loop variable.
                    fix.replace(variableTree, newContent);
                    removeSideEffectsFix.replace(variableTree, newContent);
                } else {
                    String replacement = needsBlock(usagePath) ? "{}" : "";
                    fix.merge(SuggestedFixes.replaceIncludingComments(usagePath, replacement, state));
                    removeSideEffectsFix.merge(
                            SuggestedFixes.replaceIncludingComments(usagePath, replacement, state));
                }
                continue;
            } else if (statement.getKind() == Tree.Kind.EXPRESSION_STATEMENT) {
                JCTree tree = (JCTree) ((ExpressionStatementTree) statement).getExpression();

                if (tree instanceof CompoundAssignmentTree) {
                    if (hasSideEffect(((CompoundAssignmentTree) tree).getExpression())) {
                        // If it's a compound assignment, there's no reason we'd want to remove the expression,
                        // so don't set `encounteredSideEffects` based on this usage.
                        SuggestedFix replacement =
                                SuggestedFix.replace(
                                        tree.getStartPosition(),
                                        ((JCTree.JCAssignOp) tree).getExpression().getStartPosition(),
                                        "");
                        fix.merge(replacement);
                        removeSideEffectsFix.merge(replacement);
                        continue;
                    }
                } else if (tree instanceof AssignmentTree) {
                    if (hasSideEffect(((AssignmentTree) tree).getExpression())) {
                        encounteredSideEffects = true;
                        fix.replace(
                                tree.getStartPosition(), ((JCTree.JCAssign) tree).getExpression().getStartPosition(), "");
                        removeSideEffectsFix.replace(statement, "");
                        continue;
                    }
                }
            }
            String replacement = needsBlock(usagePath) ? "{}" : "";
            fix.replace(statement, replacement);
            removeSideEffectsFix.replace(statement, replacement);
        }
        return encounteredSideEffects
                ? ImmutableList.of(removeSideEffectsFix.build(), fix.build())
                : ImmutableList.of(fix.build());
    }

    private static ImmutableList<SuggestedFix> buildUnusedParameterFixes(
            Symbol varSymbol, List<TreePath> usagePaths, VisitorState state) {
        Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) varSymbol.owner;
        int index = methodSymbol.params.indexOf(varSymbol);
        SuggestedFix.Builder fix = SuggestedFix.builder();
        for (TreePath path : usagePaths) {
            fix.delete(path.getLeaf());
        }
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                if (getSymbol(tree).equals(methodSymbol)) {
                    removeByIndex(tree.getArguments());
                }
                return super.visitMethodInvocation(tree, null);
            }

            @Override
            public Void visitMethod(MethodTree tree, Void unused) {
                if (getSymbol(tree).equals(methodSymbol)) {
                    removeByIndex(tree.getParameters());
                }
                return super.visitMethod(tree, null);
            }

            private void removeByIndex(List<? extends Tree> trees) {
                if (index >= trees.size()) {
                    // possible when removing a varargs parameter with no corresponding formal parameters
                    return;
                }
                if (trees.size() == 1) {
                    Tree tree = getOnlyElement(trees);
                    if (((JCTree) tree).getStartPosition() == -1 || state.getEndPosition(tree) == -1) {
                        // TODO(b/118437729): handle bogus source positions in enum declarations
                        return;
                    }
                    fix.delete(tree);
                    return;
                }
                int startPos;
                int endPos;
                if (index >= 1) {
                    startPos = state.getEndPosition(trees.get(index - 1));
                    endPos = state.getEndPosition(trees.get(index));
                } else {
                    startPos = ((JCTree) trees.get(index)).getStartPosition();
                    endPos = ((JCTree) trees.get(index + 1)).getStartPosition();
                }
                if (index == methodSymbol.params().size() - 1 && methodSymbol.isVarArgs()) {
                    endPos = state.getEndPosition(getLast(trees));
                }
                if (startPos == Position.NOPOS || endPos == Position.NOPOS) {
                    // TODO(b/118437729): handle bogus source positions in enum declarations
                    return;
                }
                fix.replace(startPos, endPos, "");
            }
        }.scan(state.getPath().getCompilationUnit(), null);
        return ImmutableList.of(fix.build());
    }

    private static boolean isEnhancedForLoopVar(TreePath variablePath) {
        Tree tree = variablePath.getLeaf();
        Tree parent = variablePath.getParentPath().getLeaf();
        return parent instanceof EnhancedForLoopTree
                && ((EnhancedForLoopTree) parent).getVariable() == tree;
    }

    /**
     * Looks at the list of {@code annotations} and see if there is any annotation which exists {@code
     * exemptingAnnotations}.
     */
    private static boolean exemptedByAnnotation(
            List<? extends AnnotationTree> annotations, VisitorState unused) {
        for (AnnotationTree annotation : annotations) {
            if (((JCTree.JCAnnotation) annotation).type != null) {
                Symbol.TypeSymbol tsym = ((JCTree.JCAnnotation) annotation).type.tsym;
                if (EXEMPTING_VARIABLE_ANNOTATIONS.contains(tsym.getQualifiedName().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean exemptedByName(Name name) {
        return Ascii.toLowerCase(name.toString()).startsWith(EXEMPT_PREFIX);
    }

    private class VariableFinder extends TreePathScanner<Void, Void> {
        private final Map<Symbol, TreePath> unusedElements = new HashMap<>();

        private final ListMultimap<Symbol, TreePath> usageSites = ArrayListMultimap.create();

        private final VisitorState state;

        private VariableFinder(VisitorState state) {
            this.state = state;
        }

        @Override
        public Void visitVariable(VariableTree variableTree, Void unused) {
            if (exemptedByName(variableTree.getName())) {
                return null;
            }
            if (isSuppressed(variableTree)) {
                return null;
            }
            Symbol.VarSymbol symbol = getSymbol(variableTree);
            if (symbol == null) {
                return null;
            }
            if (symbol.getKind() == ElementKind.FIELD
                    && exemptedFieldBySuperType(getType(variableTree), state)) {
                return null;
            }
            super.visitVariable(variableTree, null);
            // Return if the element is exempted by an annotation.
            if (exemptedByAnnotation(variableTree.getModifiers().getAnnotations(), state)) {
                return null;
            }
            switch (symbol.getKind()) {
                case FIELD:
                    // We are only interested in private fields and those which are not special.
                    if (isFieldEligibleForChecking(variableTree, symbol)) {
                        unusedElements.put(symbol, getCurrentPath());
                        usageSites.put(symbol, getCurrentPath());
                    }
                    break;
                case LOCAL_VARIABLE:
                    unusedElements.put(symbol, getCurrentPath());
                    usageSites.put(symbol, getCurrentPath());
                    break;
                case PARAMETER:
                    // ignore the receiver parameter
                    if (variableTree.getName().contentEquals("this")) {
                        return null;
                    }
                    unusedElements.put(symbol, getCurrentPath());
                    break;
                default:
                    break;
            }
            return null;
        }

        private boolean exemptedFieldBySuperType(Type type, VisitorState state) {
            return EXEMPTING_FIELD_SUPER_TYPES.stream()
                    .anyMatch(t -> isSubtype(type, state.getTypeFromString(t), state));
        }

        private boolean isFieldEligibleForChecking(VariableTree variableTree, Symbol.VarSymbol symbol) {
            if (reportInjectedFields
                    && variableTree.getModifiers().getFlags().isEmpty()
                    && ASTHelpers.hasDirectAnnotationWithSimpleName(variableTree, "Inject")) {
                return true;
            }
            return variableTree.getModifiers().getFlags().contains(Modifier.PRIVATE)
                    && !SPECIAL_FIELDS.contains(symbol.getSimpleName().toString())
                    && !isLoggerField(variableTree);
        }

        private boolean isLoggerField(VariableTree variableTree) {
            return variableTree.getModifiers().getFlags().containsAll(LOGGER_REQUIRED_MODIFIERS)
                    && LOGGER_TYPE_NAME.contains(variableTree.getType().toString())
                    && LOGGER_VAR_NAME.contains(variableTree.getName().toString());
        }

        @Override
        public Void visitTry(TryTree node, Void unused) {
            // Skip resources, as while these may not be referenced, they are used.
            scan(node.getBlock(), null);
            scan(node.getCatches(), null);
            scan(node.getFinallyBlock(), null);
            return null;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            if (isSuppressed(tree)) {
                return null;
            }
            if (EXEMPTING_SUPER_TYPES.stream()
                    .anyMatch(t -> isSubtype(getType(tree), Suppliers.typeFromString(t).get(state), state))) {
                return null;
            }
            return super.visitClass(tree, null);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            // skip lambda parameters
            return scan(node.getBody(), null);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            return isSuppressed(tree) ? null : super.visitMethod(tree, unused);
        }
    }

    private static final class FilterUsedVariables extends TreePathScanner<Void, Void> {
        private boolean leftHandSideAssignment = false;
        // When this greater than zero, the usage of identifiers are real.
        private int inArrayAccess = 0;
        // This is true when we are processing a `return` statement. Elements used in return statement
        // must not be considered unused.
        private boolean inReturnStatement = false;
        // When this greater than zero, the usage of identifiers are real because they are in a method
        // call.
        private int inMethodCall = 0;

        private final Set<Symbol> hasBeenAssigned = new HashSet<>();

        private TreePath currentExpressionStatement = null;

        private final Map<Symbol, TreePath> unusedElements;

        private final ListMultimap<Symbol, TreePath> usageSites;

        // Keeps track of whether a symbol was _ever_ used (between reassignments).
        private final Set<Symbol> isEverUsed = new HashSet<>();

        private final List<UnusedSpec> unusedSpecs = new ArrayList<>();

        private final ImmutableMap<Symbol, TreePath> declarationSites;

        private FilterUsedVariables(
                Map<Symbol, TreePath> unusedElements, ListMultimap<Symbol, TreePath> usageSites) {
            this.unusedElements = unusedElements;
            this.usageSites = usageSites;
            this.declarationSites = ImmutableMap.copyOf(unusedElements);
        }

        private boolean isInExpressionStatementTree() {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            return parent != null && parent.getKind() == Tree.Kind.EXPRESSION_STATEMENT;
        }

        private boolean isUsed(@Nullable Symbol symbol) {
            return symbol != null
                    && (!leftHandSideAssignment || inReturnStatement || inArrayAccess > 0 || inMethodCall > 0)
                    && unusedElements.containsKey(symbol);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            Symbol.VarSymbol symbol = getSymbol(tree);
            if (hasBeenAssigned(tree, symbol)) {
                hasBeenAssigned.add(symbol);
            }
            return super.visitVariable(tree, null);
        }

        private boolean hasBeenAssigned(VariableTree tree, Symbol.VarSymbol symbol) {
            if (symbol == null) {
                return false;
            }
            // Parameters and enhanced for loop variables are always considered assigned.
            if (symbol.getKind() == ElementKind.PARAMETER) {
                return true;
            }
            if (getCurrentPath().getParentPath().getLeaf() instanceof EnhancedForLoopTree) {
                return true;
            }
            // Otherwise it's assigned if the VariableTree has an initializer.
            if (unusedElements.containsKey(symbol) && tree.getInitializer() != null) {
                return true;
            }
            return false;
        }

        @Override
        public Void visitExpressionStatement(ExpressionStatementTree tree, Void unused) {
            currentExpressionStatement = getCurrentPath();
            super.visitExpressionStatement(tree, null);
            currentExpressionStatement = null;
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            Symbol symbol = getSymbol(tree);
            // Filtering out identifier symbol from vars map. These are real usages of identifiers.
            if (isUsed(symbol)) {
                unusedElements.remove(symbol);
            }
            if (currentExpressionStatement != null && unusedElements.containsKey(symbol)) {
                usageSites.put(symbol, currentExpressionStatement);
            }
            return null;
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void unused) {
            scan(tree.getExpression(), null);
            // If a variable is used in the left hand side of an assignment that does not count as a
            // usage.
            if (isInExpressionStatementTree()) {
                handleReassignment(tree);
                leftHandSideAssignment = true;
                scan(tree.getVariable(), null);
                leftHandSideAssignment = false;
            } else {
                super.visitAssignment(tree, null);
            }
            return null;
        }

        /**
         * Deals with assignment trees; works out if the assignment definitely overwrites the variable
         * in all ways that could be observed as we scan forwards.
         */
        private void handleReassignment(AssignmentTree tree) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (!(parent instanceof StatementTree)) {
                return;
            }
            if (tree.getVariable().getKind() != Tree.Kind.IDENTIFIER) {
                return;
            }
            if (ASTHelpers.findEnclosingNode(getCurrentPath(), ForLoopTree.class) != null) {
                return;
            }
            Symbol symbol = getSymbol(tree.getVariable());
            // Check if it was actually assigned to at this depth (or is a parameter).
            if (!((hasBeenAssigned.contains(symbol) && symbol.getKind() == ElementKind.LOCAL_VARIABLE)
                    || symbol.getKind() == ElementKind.PARAMETER)) {
                return;
            }
            if (!declarationSites.containsKey(symbol)) {
                return;
            }
            hasBeenAssigned.add(symbol);
            TreePath assignmentSite = declarationSites.get(symbol);
            if (scopeDepth(assignmentSite) != Iterables.size(getCurrentPath().getParentPath())) {
                return;
            }
            if (unusedElements.containsKey(symbol)) {
                unusedSpecs.add(UnusedSpec.of(symbol, assignmentSite, usageSites.get(symbol), tree));
            } else {
                isEverUsed.add(symbol);
            }
            unusedElements.put(symbol, getCurrentPath());
            usageSites.removeAll(symbol);
            usageSites.put(symbol, getCurrentPath().getParentPath());
        }

        // This is a crude proxy for when a variable is unconditionally overwritten. It doesn't match
        // all cases, but it catches a reassignment at the same depth.
        private static int scopeDepth(TreePath assignmentSite) {
            if (assignmentSite.getParentPath().getLeaf() instanceof EnhancedForLoopTree) {
                return Iterables.size(assignmentSite) + 1;
            }
            if (assignmentSite.getLeaf() instanceof VariableTree) {
                Symbol.VarSymbol symbol = getSymbol((VariableTree) assignmentSite.getLeaf());
                if (symbol.getKind() == ElementKind.PARAMETER) {
                    return Iterables.size(assignmentSite) + 1;
                }
            }
            return Iterables.size(assignmentSite);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree memberSelectTree, Void unused) {
            Symbol symbol = getSymbol(memberSelectTree);
            if (isUsed(symbol)) {
                unusedElements.remove(symbol);
            } else if (currentExpressionStatement != null && unusedElements.containsKey(symbol)) {
                usageSites.put(symbol, currentExpressionStatement);
            }
            // Clear leftHandSideAssignment and descend down the tree to catch any variables in the
            // receiver of this member select, which _are_ considered used.
            boolean wasLeftHandAssignment = leftHandSideAssignment;
            leftHandSideAssignment = false;
            super.visitMemberSelect(memberSelectTree, null);
            leftHandSideAssignment = wasLeftHandAssignment;
            return null;
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
            super.visitMemberReference(tree, null);
            Symbol.MethodSymbol symbol = getSymbol(tree);
            if (symbol != null) {
                symbol.getParameters().forEach(unusedElements::remove);
            }
            return null;
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
            if (isInExpressionStatementTree()) {
                leftHandSideAssignment = true;
                scan(tree.getVariable(), null);
                leftHandSideAssignment = false;
                scan(tree.getExpression(), null);
            } else {
                super.visitCompoundAssignment(tree, null);
            }
            return null;
        }

        @Override
        public Void visitArrayAccess(ArrayAccessTree node, Void unused) {
            inArrayAccess++;
            super.visitArrayAccess(node, null);
            inArrayAccess--;
            return null;
        }

        @Override
        public Void visitReturn(ReturnTree node, Void unused) {
            inReturnStatement = true;
            scan(node.getExpression(), null);
            inReturnStatement = false;
            return null;
        }

        @Override
        public Void visitUnary(UnaryTree tree, Void unused) {
            // If unary expression is inside another expression, then this is a real usage of unary
            // operand.
            // Example:
            //   array[i++] = 0; // 'i' has a real usage here. 'array' might not have.
            //   list.get(i++);
            // But if it is like this:
            //   i++;
            // Then it is possible that this is not a real usage of 'i'.
            if (isInExpressionStatementTree()
                    && (tree.getKind() == POSTFIX_DECREMENT
                    || tree.getKind() == POSTFIX_INCREMENT
                    || tree.getKind() == PREFIX_DECREMENT
                    || tree.getKind() == PREFIX_INCREMENT)) {
                leftHandSideAssignment = true;
                scan(tree.getExpression(), null);
                leftHandSideAssignment = false;
            } else {
                super.visitUnary(tree, null);
            }
            return null;
        }

        @Override
        public Void visitErroneous(ErroneousTree tree, Void unused) {
            return scan(tree.getErrorTrees(), null);
        }

        /**
         * Looks at method invocations and removes the invoked private methods from {@code
         * #unusedElements}.
         */
        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            inMethodCall++;
            super.visitMethodInvocation(tree, null);
            inMethodCall--;
            return null;
        }
    }

    interface UnusedSpec {
        /** {@link Symbol} of the unsued element. */
        Symbol symbol();

        /** {@link VariableTree} for the original declaration site. */
        TreePath variableTree();

        /**
         * All the usage sites of this variable that we claim are unused (including the initial
         * declaration/assignment).
         */
        ImmutableList<TreePath> usageSites();

        /**
         * If this usage chain was terminated by an unconditional reassignment, the corresponding {@link
         * AssignmentTree}.
         */
        Optional<AssignmentTree> terminatingAssignment();

        static UnusedSpec of(
                Symbol symbol,
                TreePath variableTree,
                Iterable<TreePath> treePaths,
                @Nullable AssignmentTree assignmentTree) {
            final ImmutableList<TreePath> treePaths1 = ImmutableList.copyOf(treePaths);
            return new UnusedSpec() {
                @Override
                public Symbol symbol() {
                    return symbol;
                }

                @Override
                public TreePath variableTree() {
                    return variableTree;
                }

                @Override
                public ImmutableList<TreePath> usageSites() {
                    return treePaths1;
                }

                @Override
                public Optional<AssignmentTree> terminatingAssignment() {
                    return Optional.ofNullable(assignmentTree);
                }
            };
        }
    }
}
