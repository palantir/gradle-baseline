/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.dataflow.AccessPathStore;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.io.Closeable;
import java.util.List;
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
import org.checkerframework.errorprone.dataflow.cfg.node.DeconstructorPatternNode;
import org.checkerframework.errorprone.dataflow.cfg.node.DoubleLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.EqualToNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ExplicitThisNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ExpressionStatementNode;
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
import org.checkerframework.errorprone.dataflow.cfg.node.StringConcatenateNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringConversionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.StringLiteralNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SuperNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SwitchExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SynchronizedNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ThrowNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.errorprone.dataflow.cfg.node.UnsignedRightShiftNode;
import org.checkerframework.errorprone.dataflow.cfg.node.VariableDeclarationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.WideningConversionNode;

/**
 * Transfer function for tracking whether EndpointServiceException subtypes can be thrown.
 * Inspired by SafetyPropagationTransfer but simplified to track boolean state.
 */
public final class ExceptionPropagationTransfer
        implements ForwardTransferFunction<ExceptionState, AccessPathStore<ExceptionState>> {

    private static final String ENDPOINT_SERVICE_EXCEPTION =
            "com.palantir.conjure.java.api.errors.EndpointServiceException";

    private static final Supplier<Type> endpointServiceExceptionSupplier =
            VisitorState.memoize(state -> state.getTypeFromString(ENDPOINT_SERVICE_EXCEPTION));

    private VisitorState state;

    @CheckReturnValue
    public ClearVisitorState setVisitorState(VisitorState visitorState) {
        this.state = visitorState;
        return new ClearVisitorState();
    }

    @Override
    public AccessPathStore<ExceptionState> initialStore(UnderlyingAST _underlyingAst, List<LocalVariableNode> _list) {
        return AccessPathStore.empty();
    }

    public final class ClearVisitorState implements Closeable {
        @Override
        public void close() {
            ExceptionPropagationTransfer.this.state = null;
        }
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitShortLiteral(
            ShortLiteralNode shortLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitIntegerLiteral(
            IntegerLiteralNode integerLiteralNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLongLiteral(
            LongLiteralNode longLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitFloatLiteral(
            FloatLiteralNode floatLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitDoubleLiteral(
            DoubleLiteralNode doubleLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitBooleanLiteral(
            BooleanLiteralNode booleanLiteralNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitCharacterLiteral(
            CharacterLiteralNode characterLiteralNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitStringLiteral(
            StringLiteralNode stringLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNullLiteral(
            NullLiteralNode nullLiteralNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNumericalMinus(
            NumericalMinusNode numericalMinusNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNumericalPlus(
            NumericalPlusNode numericalPlusNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitBitwiseComplement(
            BitwiseComplementNode bitwiseComplementNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNullChk(
            NullChkNode nullChkNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitStringConcatenate(
            StringConcatenateNode stringConcatenateNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNumericalAddition(
            NumericalAdditionNode numericalAdditionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNumericalSubtraction(
            NumericalSubtractionNode numericalSubtractionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNumericalMultiplication(
            NumericalMultiplicationNode numericalMultiplicationNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitIntegerDivision(
            IntegerDivisionNode integerDivisionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitFloatingDivision(
            FloatingDivisionNode floatingDivisionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitIntegerRemainder(
            IntegerRemainderNode integerRemainderNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitFloatingRemainder(
            FloatingRemainderNode floatingRemainderNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLeftShift(
            LeftShiftNode leftShiftNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitSignedRightShift(
            SignedRightShiftNode signedRightShiftNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitUnsignedRightShift(
            UnsignedRightShiftNode unsignedRightShiftNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitBitwiseAnd(
            BitwiseAndNode bitwiseAndNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitBitwiseOr(
            BitwiseOrNode bitwiseOrNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitBitwiseXor(
            BitwiseXorNode bitwiseXorNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLessThan(
            LessThanNode lessThanNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLessThanOrEqual(
            LessThanOrEqualNode lessThanOrEqualNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitGreaterThan(
            GreaterThanNode greaterThanNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitGreaterThanOrEqual(
            GreaterThanOrEqualNode greaterThanOrEqualNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitEqualTo(
            EqualToNode equalToNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNotEqual(
            NotEqualNode notEqualNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitConditionalAnd(
            ConditionalAndNode conditionalAndNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitConditionalOr(
            ConditionalOrNode conditionalOrNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitConditionalNot(
            ConditionalNotNode conditionalNotNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitTernaryExpression(
            TernaryExpressionNode ternaryExpressionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitSwitchExpressionNode(
            SwitchExpressionNode switchExpressionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitAssignment(
            AssignmentNode assignmentNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLocalVariable(
            LocalVariableNode localVariableNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitVariableDeclaration(
            VariableDeclarationNode variableDeclarationNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitFieldAccess(
            FieldAccessNode fieldAccessNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitMethodAccess(
            MethodAccessNode methodAccessNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitArrayAccess(
            ArrayAccessNode arrayAccessNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitImplicitThis(
            ImplicitThisNode implicitThisNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitExplicitThis(
            ExplicitThisNode explicitThisNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitSuper(
            SuperNode superNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitReturn(
            ReturnNode returnNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitLambdaResultExpression(
            LambdaResultExpressionNode lambdaResultExpressionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitStringConversion(
            StringConversionNode stringConversionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitWideningConversion(
            WideningConversionNode wideningConversionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitNarrowingConversion(
            NarrowingConversionNode narrowingConversionNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitInstanceOf(
            InstanceOfNode instanceOfNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitTypeCast(
            TypeCastNode typeCastNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitSynchronized(
            SynchronizedNode synchronizedNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitAssertionError(
            AssertionErrorNode assertionErrorNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitThrow(
            ThrowNode node, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        // Check if the thrown exception is a subtype of EndpointServiceException
        Type thrownType = ASTHelpers.getType(node.getExpression().getTree());
        Type targetType = endpointServiceExceptionSupplier.get(state);

        if (thrownType != null && targetType != null && state.getTypes().isSubtype(thrownType, targetType)) {
            // EndpointServiceException subtype is being thrown
            return noStoreChanges(ExceptionState.CAN_THROW_ENDPOINT_EXCEPTION, input);
        }

        return noStoreChanges(input.getValueOfSubNode(node.getExpression()), input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitCase(
            CaseNode caseNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitMethodInvocation(
            MethodInvocationNode node, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        // Check if any declared thrown types are EndpointServiceException subtypes
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(node.getTree());
        if (methodSymbol != null) {
            Type targetType = endpointServiceExceptionSupplier.get(state);
            if (targetType != null) {
                for (Type declaredThrownType : methodSymbol.getThrownTypes()) {
                    if (state.getTypes().isSubtype(declaredThrownType, targetType)) {
                        // Method declares it can throw EndpointServiceException subtype
                        return noStoreChanges(ExceptionState.CAN_THROW_ENDPOINT_EXCEPTION, input);
                    }
                }
            }
        }

        // Default: propagate existing state
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitObjectCreation(
            ObjectCreationNode objectCreationNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        // Default: propagate existing state
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitMemberReference(
            FunctionalInterfaceNode functionalInterfaceNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitArrayCreation(
            ArrayCreationNode arrayCreationNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitArrayType(
            ArrayTypeNode arrayTypeNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitPrimitiveType(
            PrimitiveTypeNode primitiveTypeNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitClassName(
            ClassNameNode classNameNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitPackageName(
            PackageNameNode packageNameNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitParameterizedType(
            ParameterizedTypeNode parameterizedTypeNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitMarker(
            MarkerNode markerNode, TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitClassDeclaration(
            ClassDeclarationNode classDeclarationNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitExpressionStatement(
            ExpressionStatementNode expressionStatementNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @Override
    public TransferResult<ExceptionState, AccessPathStore<ExceptionState>> visitDeconstructorPattern(
            DeconstructorPatternNode deconstructorPatternNode,
            TransferInput<ExceptionState, AccessPathStore<ExceptionState>> input) {
        return noStoreChanges(ExceptionState.NO_EXCEPTION, input);
    }

    @CheckReturnValue
    private static TransferResult<ExceptionState, AccessPathStore<ExceptionState>> noStoreChanges(
            ExceptionState value, TransferInput<?, AccessPathStore<ExceptionState>> input) {
        return new RegularTransferResult<>(value, input.getRegularStore());
    }
}
