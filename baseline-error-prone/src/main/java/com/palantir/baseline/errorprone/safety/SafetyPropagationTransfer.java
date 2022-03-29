/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone.safety;

import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.dataflow.AccessPath;
import com.google.errorprone.dataflow.AccessPathStore;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.TypeCastTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.VariableElement;
import org.checkerframework.errorprone.dataflow.analysis.ForwardTransferFunction;
import org.checkerframework.errorprone.dataflow.analysis.RegularTransferResult;
import org.checkerframework.errorprone.dataflow.analysis.TransferInput;
import org.checkerframework.errorprone.dataflow.analysis.TransferResult;
import org.checkerframework.errorprone.dataflow.cfg.UnderlyingAST;
import org.checkerframework.errorprone.dataflow.cfg.node.ArrayAccessNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ArrayCreationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ArrayTypeNode;
import org.checkerframework.errorprone.dataflow.cfg.node.AssertionErrorNode;
import org.checkerframework.errorprone.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BinaryOperationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BitwiseAndNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BitwiseComplementNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BitwiseOrNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BitwiseXorNode;
import org.checkerframework.errorprone.dataflow.cfg.node.BooleanLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.CaseNode;
import org.checkerframework.errorprone.dataflow.cfg.node.CharacterLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ClassDeclarationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ClassNameNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ConditionalAndNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ConditionalNotNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ConditionalOrNode;
import org.checkerframework.errorprone.dataflow.cfg.node.DoubleLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.EqualToNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ExplicitThisNode;
import org.checkerframework.errorprone.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.errorprone.dataflow.cfg.node.FloatLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.FloatingDivisionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.FloatingRemainderNode;
import org.checkerframework.errorprone.dataflow.cfg.node.FunctionalInterfaceNode;
import org.checkerframework.errorprone.dataflow.cfg.node.GreaterThanNode;
import org.checkerframework.errorprone.dataflow.cfg.node.GreaterThanOrEqualNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ImplicitThisNode;
import org.checkerframework.errorprone.dataflow.cfg.node.InstanceOfNode;
import org.checkerframework.errorprone.dataflow.cfg.node.IntegerDivisionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.IntegerLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.IntegerRemainderNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LambdaResultExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LeftShiftNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LessThanNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LessThanOrEqualNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.errorprone.dataflow.cfg.node.LongLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.MarkerNode;
import org.checkerframework.errorprone.dataflow.cfg.node.MethodAccessNode;
import org.checkerframework.errorprone.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NarrowingConversionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.Node;
import org.checkerframework.errorprone.dataflow.cfg.node.NotEqualNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NullChkNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NullLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NumericalAdditionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NumericalMinusNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NumericalMultiplicationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NumericalPlusNode;
import org.checkerframework.errorprone.dataflow.cfg.node.NumericalSubtractionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.PackageNameNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ParameterizedTypeNode;
import org.checkerframework.errorprone.dataflow.cfg.node.PrimitiveTypeNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ReturnNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ShortLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SignedRightShiftNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringConcatenateAssignmentNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringConcatenateNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringConversionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SuperNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SynchronizedNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ThrowNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.errorprone.dataflow.cfg.node.UnaryOperationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.UnsignedRightShiftNode;
import org.checkerframework.errorprone.dataflow.cfg.node.VariableDeclarationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.WideningConversionNode;

public final class SafetyPropagationTransfer implements ForwardTransferFunction<Safety, AccessPathStore<Safety>> {

    private VisitorState state;

    @Override
    public AccessPathStore<Safety> initialStore(UnderlyingAST _underlyingAst, List<LocalVariableNode> parameters) {
        if (parameters == null) {
            return AccessPathStore.empty();
        }
        AccessPathStore.Builder<Safety> result = AccessPathStore.<Safety>empty().toBuilder();
        for (LocalVariableNode param : parameters) {
            Safety declared = SafetyAnnotations.getSafety((Symbol) param.getElement(), state);
            result.setInformation(AccessPath.fromLocalVariable(param), declared);
        }
        return result.build();
    }

    public ClearVisitorState setVisitorState(VisitorState value) {
        this.state = Objects.requireNonNull(value, "VisitorState");
        return new ClearVisitorState();
    }

    public final class ClearVisitorState implements Closeable {
        @Override
        public void close() {
            SafetyPropagationTransfer.this.state = null;
        }
    }

    private static TransferResult<Safety, AccessPathStore<Safety>> noStoreChanges(
            Safety value, TransferInput<?, AccessPathStore<Safety>> input) {
        return new RegularTransferResult<>(value, input.getRegularStore());
    }

    @CheckReturnValue
    private static TransferResult<Safety, AccessPathStore<Safety>> updateRegularStore(
            Safety value, TransferInput<?, AccessPathStore<Safety>> input, ReadableUpdates updates) {
        ResultingStore newStore = updateStore(input.getRegularStore(), updates);
        return new RegularTransferResult<>(value, newStore.store, newStore.storeChanged);
    }

    @CheckReturnValue
    private static ResultingStore updateStore(AccessPathStore<Safety> oldStore, ReadableUpdates... updates) {
        AccessPathStore.Builder<Safety> builder = oldStore.toBuilder();
        for (ReadableUpdates update : updates) {
            for (Map.Entry<AccessPath, Safety> entry : update.values.entrySet()) {
                builder.setInformation(entry.getKey(), entry.getValue());
            }
        }
        AccessPathStore<Safety> newStore = builder.build();
        return new ResultingStore(newStore, !newStore.equals(oldStore));
    }

    @SuppressWarnings("CheckStyle")
    private static final class ResultingStore {
        final AccessPathStore<Safety> store;
        final boolean storeChanged;

        ResultingStore(AccessPathStore<Safety> store, boolean storeChanged) {
            this.store = store;
            this.storeChanged = storeChanged;
        }
    }

    interface Updates {
        void set(LocalVariableNode node, Safety value);

        void set(VariableDeclarationNode node, Safety value);

        void set(FieldAccessNode node, Safety value);

        void set(AccessPath path, Safety value);

        default void trySet(Node node, Safety value) {
            if (node instanceof LocalVariableNode) {
                set((LocalVariableNode) node, value);
            } else if (node instanceof FieldAccessNode) {
                set((FieldAccessNode) node, value);
            } else if (node instanceof VariableDeclarationNode) {
                set((VariableDeclarationNode) node, value);
            }
        }
    }

    @SuppressWarnings("CheckStyle")
    private static final class ReadableUpdates implements Updates {
        final Map<AccessPath, Safety> values = new HashMap<>();

        @Override
        public void set(LocalVariableNode node, Safety value) {
            values.put(AccessPath.fromLocalVariable(node), Objects.requireNonNull(value));
        }

        @Override
        public void set(VariableDeclarationNode node, Safety value) {
            values.put(AccessPath.fromVariableDecl(node), Objects.requireNonNull(value));
        }

        @Override
        public void set(FieldAccessNode node, Safety value) {
            AccessPath path = AccessPath.fromFieldAccess(node);
            if (path != null) {
                values.put(path, Objects.requireNonNull(value));
            }
        }

        @Override
        public void set(AccessPath path, Safety value) {
            values.put(Objects.requireNonNull(path), Objects.requireNonNull(value));
        }
    }

    private TransferResult<Safety, AccessPathStore<Safety>> literal(
            TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        // Compile-time data is guaranteed to be safe.
        return updateRegularStore(Safety.SAFE, input, updates);
    }

    private TransferResult<Safety, AccessPathStore<Safety>> unary(
            UnaryOperationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = input.getValueOfSubNode(node.getOperand());
        return noStoreChanges(safety, input);
    }

    private TransferResult<Safety, AccessPathStore<Safety>> binary(
            BinaryOperationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = input.getValueOfSubNode(node.getLeftOperand())
                .leastUpperBound(input.getValueOfSubNode(node.getRightOperand()));
        return noStoreChanges(safety, input);
    }

    // Visitor methods

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitShortLiteral(
            ShortLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitIntegerLiteral(
            IntegerLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLongLiteral(
            LongLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitFloatLiteral(
            FloatLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitDoubleLiteral(
            DoubleLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitBooleanLiteral(
            BooleanLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitCharacterLiteral(
            CharacterLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitStringLiteral(
            StringLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNullLiteral(
            NullLiteralNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return literal(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNumericalMinus(
            NumericalMinusNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNumericalPlus(
            NumericalPlusNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitBitwiseComplement(
            BitwiseComplementNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNullChk(
            NullChkNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        // null values are safe, and null check boolean results are safe
        return updateRegularStore(Safety.SAFE, input, updates);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitStringConcatenate(
            StringConcatenateNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNumericalAddition(
            NumericalAdditionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNumericalSubtraction(
            NumericalSubtractionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNumericalMultiplication(
            NumericalMultiplicationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitIntegerDivision(
            IntegerDivisionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitFloatingDivision(
            FloatingDivisionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitIntegerRemainder(
            IntegerRemainderNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitFloatingRemainder(
            FloatingRemainderNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLeftShift(
            LeftShiftNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSignedRightShift(
            SignedRightShiftNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitUnsignedRightShift(
            UnsignedRightShiftNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitBitwiseAnd(
            BitwiseAndNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitBitwiseOr(
            BitwiseOrNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitBitwiseXor(
            BitwiseXorNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitStringConcatenateAssignment(
            StringConcatenateAssignmentNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = input.getValueOfSubNode(node.getLeftOperand())
                .leastUpperBound(input.getValueOfSubNode(node.getRightOperand()));
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLessThan(
            LessThanNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLessThanOrEqual(
            LessThanOrEqualNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitGreaterThan(
            GreaterThanNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitGreaterThanOrEqual(
            GreaterThanOrEqualNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitEqualTo(
            EqualToNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNotEqual(
            NotEqualNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitConditionalAnd(
            ConditionalAndNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitConditionalOr(
            ConditionalOrNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return binary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitConditionalNot(
            ConditionalNotNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unary(node, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitTernaryExpression(
            TernaryExpressionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = input.getValueOfSubNode(node.getThenOperand())
                .leastUpperBound(input.getValueOfSubNode(node.getElseOperand()));
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitAssignment(
            AssignmentNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        Safety safety = input.getValueOfSubNode(node.getExpression());
        Node target = node.getTarget();
        if (target instanceof LocalVariableNode) {
            updates.trySet(target, safety);
        }
        if (target instanceof ArrayAccessNode) {
            updates.trySet(((ArrayAccessNode) target).getArray(), safety);
        }
        if (target instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) target;
            if (!fieldAccess.isStatic()) {
                updates.trySet(fieldAccess.getReceiver(), safety);
            }
            updates.set(fieldAccess, safety);
        }
        return updateRegularStore(safety, input, updates);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLocalVariable(
            LocalVariableNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        Safety safety = hasNonNullConstantValue(node)
                ? Safety.SAFE
                : input.getRegularStore().valueOfAccessPath(AccessPath.fromLocalVariable(node), Safety.UNKNOWN);
        return updateRegularStore(safety, input, updates);
    }

    private static boolean hasNonNullConstantValue(LocalVariableNode node) {
        if (node.getElement() instanceof VariableElement) {
            VariableElement element = (VariableElement) node.getElement();
            return (element.getConstantValue() != null);
        }
        return false;
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitVariableDeclaration(
            VariableDeclarationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        Safety safety =
                SafetyAnnotations.getSafety(ASTHelpers.getSymbol(node.getTree().getType()), state);
        return updateRegularStore(safety, input, updates);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitFieldAccess(
            FieldAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety fieldSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(node.getTree()), state);
        Safety typeSafety = SafetyAnnotations.getSafety(ASTHelpers.getType(node.getTree()).tsym, state);
        Safety safety = Safety.mergeAssumingUnknownIsSame(fieldSafety, typeSafety);
        return updateRegularStore(safety, input, new ReadableUpdates());
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMethodAccess(
            MethodAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayAccess(
            ArrayAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitImplicitThis(
            ImplicitThisNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitExplicitThis(
            ExplicitThisNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSuper(
            SuperNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitReturn(
            ReturnNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Node result = node.getResult();
        Safety safety = result == null ? Safety.SAFE : input.getValueOfSubNode(result);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLambdaResultExpression(
            LambdaResultExpressionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitStringConversion(
            StringConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = input.getValueOfSubNode(node.getOperand());
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitWideningConversion(
            WideningConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety valueSafety = input.getValueOfSubNode(node.getOperand());
        Safety widenTargetSafety = SafetyAnnotations.getSafety(ASTHelpers.getType(node.getTree()).tsym, state);
        Safety resultSafety = Safety.mergeAssumingUnknownIsSame(valueSafety, widenTargetSafety);
        return updateRegularStore(resultSafety, input, new ReadableUpdates());
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNarrowingConversion(
            NarrowingConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety valueSafety = input.getValueOfSubNode(node.getOperand());
        Safety narrowTargetSafety = SafetyAnnotations.getSafety(ASTHelpers.getType(node.getTree()).tsym, state);
        Safety resultSafety = Safety.mergeAssumingUnknownIsSame(valueSafety, narrowTargetSafety);
        return updateRegularStore(resultSafety, input, new ReadableUpdates());
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitInstanceOf(
            InstanceOfNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitTypeCast(
            TypeCastNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety valueSafety = input.getValueOfSubNode(node.getOperand());
        TypeCastTree castTree = (TypeCastTree) node.getTree();
        Safety castTargetSafety = SafetyAnnotations.getSafety(ASTHelpers.getType(castTree.getType()).tsym, state);
        Safety resultSafety = Safety.mergeAssumingUnknownIsSame(valueSafety, castTargetSafety);
        return updateRegularStore(resultSafety, input, new ReadableUpdates());
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSynchronized(
            SynchronizedNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitAssertionError(
            AssertionErrorNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitThrow(
            ThrowNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitCase(
            CaseNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMethodInvocation(
            MethodInvocationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety result = SafetyAnnotations.getMethodInvocationResultSafety(node.getTree(), state)
                .orElse(Safety.UNKNOWN);
        ReadableUpdates updates = new ReadableUpdates();
        return updateRegularStore(result, input, updates);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitObjectCreation(
            ObjectCreationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety result = SafetyAnnotations.getSafety(node.getTree(), state);
        ReadableUpdates updates = new ReadableUpdates();
        return updateRegularStore(result, input, updates);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMemberReference(
            FunctionalInterfaceNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayCreation(
            ArrayCreationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayType(
            ArrayTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitPrimitiveType(
            PrimitiveTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitClassName(
            ClassNameNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitPackageName(
            PackageNameNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitParameterizedType(
            ParameterizedTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMarker(
            MarkerNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitClassDeclaration(
            ClassDeclarationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return stubUnknown(input);
    }

    private static TransferResult<Safety, AccessPathStore<Safety>> stubUnknown(
            TransferInput<?, AccessPathStore<Safety>> input) {
        return new RegularTransferResult<>(Safety.UNKNOWN, input.getRegularStore());
    }
}
