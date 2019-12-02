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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility functionality that does not exist in {@link com.google.errorprone.util.ASTHelpers}. */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
final class MoreASTHelpers {

    private static final String RUNTIME_EXCEPTION = RuntimeException.class.getName();
    private static final String ERROR = Error.class.getName();
    private static final String THROWABLE = Throwable.class.getName();

    /** Returns true if the provided type represents a checked exception. */
    static boolean isCheckedException(Type type, VisitorState state) {
        Types types = state.getTypes();
        return types.isSubtype(type, state.getTypeFromString(THROWABLE))
                && !types.isSubtype(type, state.getTypeFromString(RUNTIME_EXCEPTION))
                && !types.isSubtype(type, state.getTypeFromString(ERROR));
    }

    /** Gets thrown exceptions from a {@link TryTree} excluding those thrown from {@link CatchTree}. */
    static ImmutableSet<Type> getThrownExceptionsFromTryBody(TryTree tree, VisitorState state) {
        ImmutableSet.Builder<Type> results = ImmutableSet.builder();
        results.addAll(getThrownExceptions(tree.getBlock(), state));
        tree.getResources().forEach(resource -> {
            results.addAll(getThrownExceptions(resource, state));
            Type resourceType = ASTHelpers.getType(resource);
            if (resourceType != null && resourceType.tsym instanceof Symbol.ClassSymbol) {
                MoreASTHelpers.getCloseMethod((Symbol.ClassSymbol) resourceType.tsym, state)
                        .map(Symbol.MethodSymbol::getThrownTypes)
                        .ifPresent(results::addAll);
            }
        });
        return results.build();
    }

    /**
     * Returns all exceptions thrown by
     * getThrownExceptions and associated utilities are borrowed from upstream error-prone with an Apache 2 license.
     * https://github.com/google/error-prone/blob/8df5e4bbae8368b62ec96e4563fb1f448229f3e9/core/src/main/java/com/google/errorprone/bugpatterns/InterruptedExceptionSwallowed.java
     */
    static ImmutableSet<Type> getThrownExceptions(Tree tree, VisitorState state) {
        ScanThrownTypes scanner = new ScanThrownTypes(state);
        scanner.scan(tree, null);
        return ImmutableSet.copyOf(scanner.getThrownTypes());
    }

    /** Returns an optional of the {@link AutoCloseable#close()} method on the provided symbol. */
    private static Optional<Symbol.MethodSymbol> getCloseMethod(Symbol.ClassSymbol symbol, VisitorState state) {
        Types types = state.getTypes();
        return symbol.getEnclosedElements().stream()
                .filter(sym ->
                        types.isAssignable(symbol.type, state.getTypeFromString(AutoCloseable.class.getName()))
                                && sym.getSimpleName().contentEquals("close")
                                && sym.getTypeParameters().isEmpty())
                .map(e -> (Symbol.MethodSymbol) e)
                .findFirst();
    }

    /** Returns either the input type, or the types that make up the union in the case of a union type. */
    static ImmutableList<Type> expandUnion(@Nullable Type type) {
        if (type == null) {
            return ImmutableList.of();
        }
        if (type.isUnion()) {
            Type.UnionClassType unionType = (Type.UnionClassType) type;
            return ImmutableList.copyOf(unionType.getAlternativeTypes());
        }
        return ImmutableList.of(type);
    }

    private static final class ScanThrownTypes extends TreeScanner<Void, Void> {
        private boolean inResources = false;
        private final Deque<Set<Type>> thrownTypes = new ArrayDeque<>();

        private final VisitorState state;
        private final Types types;

        ScanThrownTypes(VisitorState state) {
            this.state = state;
            this.types = state.getTypes();
            this.thrownTypes.push(new HashSet<>());
        }

        private Set<Type> getThrownTypes() {
            return thrownTypes.peek();
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
            Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(invocation);
            if (symbol != null) {
                getThrownTypes().addAll(symbol.getThrownTypes());
            }
            return super.visitMethodInvocation(invocation, null);
        }

        @Override
        public Void visitTry(TryTree tree, Void unused) {
            thrownTypes.push(new HashSet<>());
            inResources = true;
            scan(tree.getResources(), null);
            inResources = false;
            scan(tree.getBlock(), null);
            scan(tree.getCatches(), null);
            scan(tree.getFinallyBlock(), null);
            Set<Type> fromBlock = thrownTypes.pop();
            getThrownTypes().addAll(fromBlock);
            return null;
        }

        @Override
        public Void visitCatch(CatchTree tree, Void unused) {
            Type type = ASTHelpers.getType(tree.getParameter());
            // Remove the thrown types; if they're re-thrown they'll get re-added.
            for (Type unionMember : expandUnion(type)) {
                getThrownTypes().removeIf(thrownType -> types.isAssignable(thrownType, unionMember));
            }
            return super.visitCatch(tree, null);
        }

        @Override
        public Void visitThrow(ThrowTree tree, Void unused) {
            getThrownTypes().addAll(expandUnion(ASTHelpers.getType(tree.getExpression())));
            return super.visitThrow(tree, null);
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void unused) {
            Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
            if (symbol != null) {
                getThrownTypes().addAll(symbol.getThrownTypes());
            }
            return super.visitNewClass(tree, null);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            if (inResources) {
                Symbol symbol = ASTHelpers.getSymbol(tree.getType());
                if (symbol instanceof Symbol.ClassSymbol) {
                    Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) symbol;
                    Optional<Symbol.MethodSymbol> methodSymbol = getCloseMethod(classSymbol, state);
                    methodSymbol.map(Symbol.MethodSymbol::getThrownTypes).ifPresent(getThrownTypes()::addAll);
                }
            }
            return super.visitVariable(tree, null);
        }

        // We don't need to account for anything thrown by declarations
        @Override
        public Void visitLambdaExpression(LambdaExpressionTree tree, Void unused) {
            return null;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            return null;
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            return null;
        }
    }

    private MoreASTHelpers() {}
}
