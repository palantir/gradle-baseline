/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Scope.LookupKind;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Filter;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ClassInitializationDeadlock",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary =
                "Static fields and blocks within a class must not require a subclass, otherwise a deadlock occurs when"
                        + " separate threads simultaneously attempt to initialize the subclass and base class. This"
                        + " behavior is allowed by the java language specification and is not considered a Java bug.\n"
                        + "From https://docs.oracle.com/javase/specs/jls/se8/html/jls-12.html#jls-12.4.1\n"
                        + "> 12.4.1. When Initialization Occurs\n"
                        + "> A class or interface type T will be initialized immediately before the first occurrence of"
                        + " any one of the following:\n"
                        + "> • T is a class and an instance of T is created.\n"
                        + "> • A static method declared by T is invoked.\n"
                        + "> • A static field declared by T is assigned.\n"
                        + "> • A static field declared by T is used and the field is not "
                        + "a constant variable (§4.12.4).\n"
                        + "> • T is a top level class (§7.6) and an assert statement (§14.10) lexically nested within T"
                        + " (§8.1.3) is executed.")
public final class ClassInitializationDeadlock extends BugChecker implements BugChecker.ClassTreeMatcher {

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        SubtypeInitializationVisitor visitor = new SubtypeInitializationVisitor(ASTHelpers.getType(tree));
        for (Tree member : tree.getMembers()) {
            switch (member.getKind()) {
                case VARIABLE:
                    // Simplify to member.accept?
                    VariableTree variableTree = (VariableTree) member;
                    ExpressionTree initializer = variableTree.getInitializer();
                    if (initializer != null
                            && ASTHelpers.getSymbol(variableTree).isStatic()) {
                        initializer.accept(visitor, state);
                    }
                    break;
                case BLOCK:
                    // No symbol for static blocks, nor is it possible to define a non-static top level member
                    // block.
                    member.accept(visitor, state);
                    break;
                default:
                    // Continue
            }
        }
        return Description.NO_MATCH;
    }

    private final class SubtypeInitializationVisitor extends TreeScanner<Void, VisitorState> {

        private final Type baseType;

        SubtypeInitializationVisitor(Type baseType) {
            this.baseType = baseType;
        }

        @Override
        public Void visitNewClass(NewClassTree node, VisitorState state) {
            if (isSubtype(ASTHelpers.getType(node), baseType, state)
                    // Anonymous inner subtypes are allowed
                    && !ASTHelpers.isSameType(ASTHelpers.getType(node.getIdentifier()), baseType, state)
                    // Private subtypes are allowed because they can only be initialized through the enclosing class
                    && !isPrivateEncapsulated(node)) {
                state.reportMatch(describeMatch(node));
                return null;
            }
            return super.visitNewClass(node, state);
        }

        private boolean isPrivateEncapsulated(NewClassTree node) {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
            if (methodSymbol == null) {
                return false;
            }
            ClassSymbol newClassSymbol = methodSymbol.enclClass();
            if (!newClassSymbol.getQualifiedName().startsWith(baseType.tsym.getQualifiedName())) {
                // Not encapsulated
                return false;
            }
            return cannotBeInitializedExternally(newClassSymbol);
        }

        private boolean cannotBeInitializedExternally(ClassSymbol newClassSymbol) {
            if (newClassSymbol.isPrivate()) {
                return true;
            }
            return !newClassSymbol
                    .members()
                    .getSymbols(CanBeExternallyInitializedFilter.INSTANCE, LookupKind.NON_RECURSIVE)
                    .iterator()
                    .hasNext();
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
            // True if the method invocation is a static method on a subtype
            if (methodSymbol != null
                    && methodSymbol.isStatic()
                    && isSubtype(ASTHelpers.enclosingClass(methodSymbol).type, baseType, state)) {
                state.reportMatch(describeMatch(node));
                return null;
            }
            // True if the method invocation returns a subtype of the target
            if (isSubtype(ASTHelpers.getResultType(node), baseType, state)) {
                state.reportMatch(describeMatch(node));
                return null;
            }
            return super.visitMethodInvocation(node, state);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, VisitorState state) {
            if (handleAssignment(node.getVariable(), state)) {
                return null;
            }
            return super.visitAssignment(node, state);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, VisitorState state) {
            if (handleAssignment(node.getVariable(), state)) {
                return null;
            }
            return super.visitCompoundAssignment(node, state);
        }

        private boolean handleAssignment(ExpressionTree variable, VisitorState state) {
            // Matches if a subtype static field assignment occurs
            Symbol symbol = ASTHelpers.getSymbol(variable);
            if (symbol != null && symbol.isStatic() && symbol instanceof VarSymbol) {
                VarSymbol varSymbol = (VarSymbol) symbol;
                if (isSubtype(varSymbol.owner.type, baseType, state)) {
                    state.reportMatch(describeMatch(variable));
                    return true;
                }
            }
            return false;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, VisitorState state) {
            Symbol symbol = ASTHelpers.getSymbol(node);
            if (symbol != null && symbol.isStatic() && symbol instanceof VarSymbol) {
                VarSymbol varSymbol = (VarSymbol) symbol;
                // Constant values may be accessed without forcing initialization
                if (varSymbol.getConstValue() == null && isSubtype(varSymbol.owner.type, baseType, state)) {
                    state.reportMatch(describeMatch(node));
                    return null;
                }
            }
            return super.visitMemberSelect(node, state);
        }

        // Do not traverse out of scope
        @Override
        public Void visitClass(ClassTree _node, VisitorState _state) {
            return null;
        }

        // Do not traverse out of scope
        @Override
        public Void visitMethod(MethodTree _node, VisitorState _state) {
            return null;
        }

        // Do not traverse out of scope
        @Override
        public Void visitLambdaExpression(LambdaExpressionTree _node, VisitorState _state) {
            return null;
        }
    }

    // Returns true if maybeSubtype is a subtype of baseType, but not if they are the same type
    private static boolean isSubtype(Type maybeSubtype, Type baseType, VisitorState state) {
        if (maybeSubtype == null || baseType == null) {
            return false;
        }
        return ASTHelpers.isSubtype(maybeSubtype, baseType, state)
                && !ASTHelpers.isSameType(maybeSubtype, baseType, state);
    }

    private enum CanBeExternallyInitializedFilter implements Filter<Symbol> {
        INSTANCE;

        @Override
        public boolean accepts(Symbol symbol) {
            switch (symbol.getKind()) {
                case FIELD:
                    // fall through
                case METHOD:
                    if (symbol.isStatic() && !symbol.isPrivate()) {
                        return true;
                    }
                    break;
                case CONSTRUCTOR:
                    if (!symbol.isPrivate()) {
                        return true;
                    }
                    break;
                default:
                    // fall through
            }
            return false;
        }
    }
}
