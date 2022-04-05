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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.dataflow.AccessPath;
import com.google.errorprone.dataflow.AccessPathStore;
import com.google.errorprone.dataflow.AccessPathValues;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import java.io.Closeable;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.BaseStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.lang.model.element.VariableElement;
import org.checkerframework.errorprone.dataflow.analysis.Analysis;
import org.checkerframework.errorprone.dataflow.analysis.ForwardAnalysisImpl;
import org.checkerframework.errorprone.dataflow.analysis.ForwardTransferFunction;
import org.checkerframework.errorprone.dataflow.analysis.RegularTransferResult;
import org.checkerframework.errorprone.dataflow.analysis.TransferInput;
import org.checkerframework.errorprone.dataflow.analysis.TransferResult;
import org.checkerframework.errorprone.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.errorprone.dataflow.cfg.UnderlyingAST;
import org.checkerframework.errorprone.dataflow.cfg.builder.CFGBuilder;
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
import org.checkerframework.errorprone.dataflow.cfg.node.SwitchExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.SynchronizedNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.errorprone.dataflow.cfg.node.ThrowNode;
import org.checkerframework.errorprone.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.errorprone.dataflow.cfg.node.UnaryOperationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.UnsignedRightShiftNode;
import org.checkerframework.errorprone.dataflow.cfg.node.VariableDeclarationNode;
import org.checkerframework.errorprone.dataflow.cfg.node.WideningConversionNode;
import org.checkerframework.errorprone.javacutil.TreePathUtil;

/**
 * Heavily modified fork from error-prone NullnessPropagationTransfer (apache 2).
 * @see <a href="https://github.com/google/error-prone/blob/v2.11.0/check_api/src/main/java/com/google/errorprone/dataflow/nullnesspropagation/NullnessPropagationTransfer.java">NullnessPropagationTransfer</a>
 */
public final class SafetyPropagationTransfer implements ForwardTransferFunction<Safety, AccessPathStore<Safety>> {

    private static final Matcher<ExpressionTree> TO_STRING =
            MethodMatchers.instanceMethod().anyClass().named("toString").withNoParameters();

    private static final Matcher<ExpressionTree> THROWABLE_GET_MESSAGE = MethodMatchers.instanceMethod()
            .onDescendantOf(Throwable.class.getName())
            .namedAnyOf("getMessage", "getLocalizedMessage")
            .withNoParameters();

    private static final Matcher<ExpressionTree> ITERABLE_FOR_EACH = MethodMatchers.instanceMethod()
            .onDescendantOf(Iterable.class.getName())
            .named("forEach")
            .withParameters(Consumer.class.getName());

    private static final Matcher<ExpressionTree> STRING_FORMAT =
            MethodMatchers.staticMethod().onClass(String.class.getName()).named("format");

    private static final Matcher<ExpressionTree> OBJECTS_TO_STRING =
            MethodMatchers.staticMethod().onClass(Objects.class.getName()).named("toString");

    private static final Matcher<ExpressionTree> IMMUTABLE_COLLECTION_FACTORY = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClassAny(
                            ImmutableList.class.getName(),
                            ImmutableSet.class.getName(),
                            ImmutableSortedSet.class.getName(),
                            ImmutableMap.class.getName(),
                            ImmutableListMultimap.class.getName(),
                            ImmutableSetMultimap.class.getName(),
                            List.class.getName(),
                            Set.class.getName(),
                            Map.class.getName())
                    .namedAnyOf("of", "copyOf"),
            MethodMatchers.staticMethod().onClass(Arrays.class.getName()).named("asList"));

    private static final Matcher<ExpressionTree> OPTIONAL_FACTORIES = Matchers.anyOf(
            MethodMatchers.staticMethod().onClass(Optional.class.getName()).namedAnyOf("of", "ofNullable"),
            MethodMatchers.staticMethod()
                    .onClassAny(
                            OptionalInt.class.getName(), OptionalLong.class.getName(), OptionalDouble.class.getName())
                    .named("of"));

    private static final Matcher<ExpressionTree> STATIC_STREAM_FACTORIES =
            MethodMatchers.staticMethod().onClass(Stream.class.getName()).namedAnyOf("of", "ofNullable", "concat");

    // These methods do not take the receiver (generally a static class) into account, only the inputs.
    private static final Matcher<ExpressionTree> RETURNS_SAFETY_COMBINATION_OF_ARGS = Matchers.anyOf(
            STRING_FORMAT,
            OBJECTS_TO_STRING,
            IMMUTABLE_COLLECTION_FACTORY,
            OPTIONAL_FACTORIES,
            STATIC_STREAM_FACTORIES);

    private static final Matcher<ExpressionTree> OPTIONAL_ACCESSORS = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Optional.class.getName())
                    .namedAnyOf("filter", "get", "orElseThrow", "stream"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(OptionalInt.class.getName())
                    .namedAnyOf("getAsInt", "orElseThrow"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(OptionalLong.class.getName())
                    .namedAnyOf("getAsLong", "orElseThrow"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(OptionalDouble.class.getName())
                    .namedAnyOf("getAsDouble", "orElseThrow"));

    private static final Matcher<ExpressionTree> STREAM_ACCESSORS = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(BaseStream.class.getName())
                    .namedAnyOf("iterator", "spliterator", "sequential", "parallel", "unordered", "onClose"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getName())
                    .namedAnyOf(
                            "filter",
                            "distinct",
                            "sorted",
                            "peek",
                            "limit",
                            "skip",
                            "takeWhile",
                            "dropWhile",
                            "toArray",
                            "toList",
                            "min",
                            "max",
                            "findFirst",
                            "findAny"));

    // Returns the safety of the receiver, e.g. myString.getBytes() returns the safety of myString.
    private static final Matcher<ExpressionTree> RETURNS_SAFETY_OF_RECEIVER = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(CharSequence.class.getName())
                    .namedAnyOf("charAt", "subSequence", "chars", "codePoints"),
            MethodMatchers.instanceMethod()
                    .onExactClass(String.class.getName())
                    .namedAnyOf("getBytes", "toLowerCase", "toUpperCase", "substring", "split", "toCharArray"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Collection.class.getName())
                    .namedAnyOf("toArray", "stream", "parallelStream"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Iterable.class.getName())
                    .namedAnyOf("toArray", "iterator", "spliterator"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Iterator.class.getName())
                    .named("next")
                    .withNoParameters(),
            OPTIONAL_ACCESSORS,
            STREAM_ACCESSORS);

    private static final Matcher<ExpressionTree> RETURNS_SAFETY_OF_FIRST_ARG = Matchers.anyOf(
            MethodMatchers.staticMethod().onClass(Objects.class.getName()).named("requireNonNull"),
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.base.Preconditions")
                    .named("checkNotNull"),
            MethodMatchers.staticMethod()
                    .onClass("com.palantir.logsafe.Preconditions")
                    .namedAnyOf("checkNotNull", "checkArgumentNotNull"));

    private static final Matcher<ExpressionTree> RETURNS_SAFETY_OF_ARGS_AND_RECEIVER = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Stream.class.getName())
                    .namedAnyOf("collect"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(Optional.class.getName())
                    // TODO(ckozak): support 'or' and 'orElseGet' which require lambda support
                    .named("orElse"));

    private VisitorState state;
    private final Set<VarSymbol> traversed;
    private final ImmutableMap<Symbol, Safety> known;
    private final AccessPathStore<Safety> initialStore;

    SafetyPropagationTransfer() {
        known = ImmutableMap.of();
        traversed = new HashSet<>();
        initialStore = AccessPathStore.empty();
    }

    private SafetyPropagationTransfer(
            Map<Symbol, Safety> known,
            AccessPathStore<Safety> initialStore,
            Set<VarSymbol> traversed,
            VisitorState state) {
        this.known = ImmutableMap.copyOf(known);
        this.traversed = new HashSet<>(traversed);
        this.initialStore = initialStore;
        setVisitorState(state);
    }

    SafetyPropagationTransfer withSafety(ImmutableMap<Symbol, Safety> newKnown, AccessPathStore<Safety> initial) {
        Map<Symbol, Safety> combined = new HashMap<>(known.size() + newKnown.size());
        combined.putAll(this.known);
        combined.putAll(newKnown);
        return new SafetyPropagationTransfer(combined, initial, traversed, state);
    }

    @Override
    public AccessPathStore<Safety> initialStore(UnderlyingAST _underlyingAst, List<LocalVariableNode> parameters) {
        if (parameters == null) {
            return initialStore;
        }
        AccessPathStore.Builder<Safety> result = initialStore.toBuilder();

        for (LocalVariableNode param : parameters) {
            Safety safety = SafetyAnnotations.getSafety((Symbol) param.getElement(), state);
            Symbol parameterSymbol = ASTHelpers.getSymbol(param.getTree());
            if (parameterSymbol != null) {
                Safety knownSafety = known.get(parameterSymbol);
                if (knownSafety != null) {
                    safety = Safety.mergeAssumingUnknownIsSame(safety, knownSafety);
                }
            }
            result.setInformation(AccessPath.fromLocalVariable(param), safety);
        }
        return result.build();
    }

    public ClearVisitorState setVisitorState(VisitorState value) {
        this.state = Objects.requireNonNull(value, "VisitorState");
        traversed.clear();
        return new ClearVisitorState();
    }

    public final class ClearVisitorState implements Closeable {
        @Override
        public void close() {
            SafetyPropagationTransfer.this.state = null;
            traversed.clear();
        }
    }

    @CheckReturnValue
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
    private static ResultingStore updateStore(AccessPathStore<Safety> oldStore, ReadableUpdates update) {
        AccessPathStore.Builder<Safety> builder = oldStore.toBuilder();
        update.values.forEach(builder::setInformation);
        AccessPathStore<Safety> newStore = builder.build();
        return new ResultingStore(newStore, !newStore.equals(oldStore));
    }

    @SuppressWarnings("CheckStyle")
    private static final class ResultingStore {
        private final AccessPathStore<Safety> store;
        private final boolean storeChanged;

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

        boolean isEmpty();
    }

    private static final class ReadableUpdates implements Updates {
        private final Map<AccessPath, Safety> values = new HashMap<>();

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

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }
    }

    @CheckReturnValue
    private static TransferResult<Safety, AccessPathStore<Safety>> unknown(
            TransferInput<?, AccessPathStore<Safety>> input) {
        return noStoreChanges(Safety.UNKNOWN, input);
    }

    @CheckReturnValue
    private TransferResult<Safety, AccessPathStore<Safety>> literal(
            TransferInput<Safety, AccessPathStore<Safety>> input) {
        // Compile-time data (literal) is guaranteed to be safe.
        return noStoreChanges(Safety.SAFE, input);
    }

    @CheckReturnValue
    private TransferResult<Safety, AccessPathStore<Safety>> unary(
            UnaryOperationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = getValueOfSubNode(input, node.getOperand());
        return noStoreChanges(safety, input);
    }

    @CheckReturnValue
    private TransferResult<Safety, AccessPathStore<Safety>> binary(
            BinaryOperationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = getValueOfSubNode(input, node.getLeftOperand())
                .leastUpperBound(getValueOfSubNode(input, node.getRightOperand()));
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
        // null values are safe, and null check boolean results are safe
        return noStoreChanges(Safety.SAFE, input);
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
        Safety safety = getValueOfSubNode(input, node.getLeftOperand())
                .leastUpperBound(getValueOfSubNode(input, node.getRightOperand()));
        ReadableUpdates updates = new ReadableUpdates();
        updates.trySet(node.getLeftOperand(), safety);
        return updateRegularStore(safety, input, updates);
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
        Safety safety = getValueOfSubNode(input, node.getThenOperand())
                .leastUpperBound(getValueOfSubNode(input, node.getElseOperand()));
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSwitchExpressionNode(
            SwitchExpressionNode _node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitAssignment(
            AssignmentNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        ReadableUpdates updates = new ReadableUpdates();
        Safety expressionSafety = getValueOfSubNode(input, node.getExpression());
        Safety targetSymbolSafety = SafetyAnnotations.getSafety(
                ASTHelpers.getSymbol(node.getTarget().getTree()), state);
        Safety safety = Safety.mergeAssumingUnknownIsSame(expressionSafety, targetSymbolSafety);
        Node target = node.getTarget();
        if (target instanceof LocalVariableNode) {
            updates.trySet(target, safety);
        } else if (target instanceof ArrayAccessNode) {
            Node arrayNode = ((ArrayAccessNode) target).getArray();
            Safety arrayNodeSafety = getValueOfSubNode(input, arrayNode);
            safety = arrayNodeSafety == null ? safety : arrayNodeSafety.leastUpperBound(safety);
            updates.trySet(arrayNode, safety);
        } else if (target instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) target;
            updates.set(fieldAccess, safety);
        } else {
            throw new UnsupportedOperationException(
                    "Safety analysis bug, unknown target type: " + target.getClass() + " with value: " + target);
        }
        return updateRegularStore(safety, input, updates);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLocalVariable(
            LocalVariableNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        if (hasNonNullConstantValue(node)) {
            return noStoreChanges(Safety.SAFE, input);
        }
        AccessPath accessPath = AccessPath.fromLocalVariable(node);
        // No safety information found, likely a captured reference used within a lambda or anonymous class.
        Safety safety = input.getRegularStore().valueOfAccessPath(accessPath, null);
        if (safety != null) {
            if (safety == Safety.UNKNOWN) {
                TreePath currentPath = TreePath.getPath(state.getPath().getCompilationUnit(), node.getTree());
                TreePath methodOrLambda =
                        TreePathUtil.pathTillOfKind(currentPath, EnumSet.of(Kind.METHOD, Kind.LAMBDA_EXPRESSION));
                Tree methodOrLambdaParent = methodOrLambda.getParentPath().getLeaf();
                // TODO(ckozak): Check if the methodOrLambdaParent (with perhaps additional unwrapping for
                // params/cast/etc) is a MethodInvocationTree which passes safety information from the receiver, for
                // example iterable.forEach(item -> /* item is as safe as iterable */), and resolve safety from the
                // parent.
            }

            return noStoreChanges(safety, input);
        }

        Symbol symbol = ASTHelpers.getSymbol(node.getTree());
        if (!(symbol instanceof VarSymbol)) {
            return unknown(input);
        }
        VarSymbol variableSymbol = (VarSymbol) symbol;
        Symbol variableOwner = variableSymbol.owner;
        if (variableOwner == null) {
            return unknown(input);
        }
        Tree tree = findEnclosingWithSymbol(state.getPath(), variableOwner);
        if (tree == null) {
            return unknown(input);
        }
        if (!(tree instanceof MethodTree)) {
            return unknown(input);
        }

        Tree variableDefinitionTree = tree.accept(
                new TreeScanner<Tree, Symbol>() {
                    @Override
                    public Tree visitVariable(VariableTree node, Symbol sym) {
                        Symbol symbol = ASTHelpers.getSymbol(node);
                        if (Objects.equals(symbol, sym)) {
                            return node;
                        }
                        return null;
                    }

                    @Override
                    public Tree reduce(Tree first, Tree second) {
                        return first != null ? first : second;
                    }
                },
                variableSymbol);
        if (variableDefinitionTree == null) {
            return unknown(input);
        }
        TreePath variableDefinition = TreePath.getPath(state.getPath().getCompilationUnit(), variableDefinitionTree);
        if (variableDefinition == null) {
            return unknown(input);
        }
        Tree enclosing = TreePathUtil.enclosingMethodOrLambda(variableDefinition);
        if (enclosing == null) {
            return unknown(input);
        }
        TreePath enclosingPath = TreePath.getPath(state.getPath().getCompilationUnit(), enclosing);
        JavacProcessingEnvironment javacEnv = JavacProcessingEnvironment.instance(state.context);
        UnderlyingAST ast = createAst(enclosingPath, enclosing);
        ControlFlowGraph cfg = CFGBuilder.build(state.getPath().getCompilationUnit(), ast, false, false, javacEnv);
        Analysis<Safety, AccessPathStore<Safety>, SafetyPropagationTransfer> analysis = new ForwardAnalysisImpl<>(this);
        analysis.performAnalysis(cfg);
        Safety maybeResult = analysis.getValue(variableDefinitionTree);
        return noStoreChanges(maybeResult == null ? Safety.UNKNOWN : maybeResult, input);
    }

    private static UnderlyingAST createAst(TreePath path, Tree tree) {
        if (tree instanceof MethodTree) {
            return new UnderlyingAST.CFGMethod((MethodTree) tree, TreePathUtil.enclosingClass(path));
        }

        if (tree instanceof LambdaExpressionTree) {
            return new UnderlyingAST.CFGLambda(
                    (LambdaExpressionTree) tree, TreePathUtil.enclosingClass(path), TreePathUtil.enclosingMethod(path));
        }

        throw new UnsupportedOperationException("Unknown and unexpected tree type: " + tree);
    }

    @Nullable
    private static Tree findEnclosingWithSymbol(TreePath treePath, Symbol search) {
        if (search == null) {
            return null;
        }
        for (TreePath current = treePath; current != null; current = current.getParentPath()) {
            Tree leaf = current.getLeaf();
            if (leaf == null) {
                return null;
            }
            Symbol leafSym = ASTHelpers.getSymbol(leaf);
            if (Objects.equals(leafSym, search)) {
                return leaf;
            }
        }
        return null;
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
        Safety variableTypeSafety =
                SafetyAnnotations.getSafety(ASTHelpers.getSymbol(node.getTree().getType()), state);
        Safety variableSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(node.getTree()), state);
        Safety safety = Safety.mergeAssumingUnknownIsSame(variableTypeSafety, variableSafety);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitFieldAccess(
            FieldAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety fieldSafety = SafetyAnnotations.getSafety(ASTHelpers.getSymbol(node.getTree()), state);
        Type fieldType = ASTHelpers.getType(node.getTree());
        Safety typeSafety = fieldType == null ? Safety.UNKNOWN : SafetyAnnotations.getSafety(fieldType.tsym, state);
        VarSymbol symbol = (VarSymbol) ASTHelpers.getSymbol(node.getTree());
        AccessPath maybeAccessPath = AccessPath.fromFieldAccess(node);
        Safety flowSafety = fieldSafety(symbol, maybeAccessPath, input.getRegularStore());
        Safety safety = Safety.mergeAssumingUnknownIsSame(fieldSafety, typeSafety, flowSafety);
        return noStoreChanges(safety, input);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private Safety fieldSafety(
            @Nullable VarSymbol accessed, @Nullable AccessPath path, AccessPathValues<Safety> store) {
        if (accessed == null) {
            return Safety.UNKNOWN;
        }
        Safety maybeFlowComputedSafety = (path == null) ? null : store.valueOfAccessPath(path, null);
        Safety flowSafety = maybeFlowComputedSafety == null ? Safety.UNKNOWN : maybeFlowComputedSafety;
        Safety symbolSafety = SafetyAnnotations.getSafety(accessed, state);
        Safety typeSafety = SafetyAnnotations.getSafety(accessed.type.tsym, state);
        Safety symbolAndTypeSafety = Safety.mergeAssumingUnknownIsSame(flowSafety, symbolSafety, typeSafety);
        if (accessed.getSimpleName().contentEquals("class")) {
            // compile-time constant
            return Safety.mergeAssumingUnknownIsSame(symbolAndTypeSafety, Safety.SAFE);
        }
        if (accessed.isEnum()) {
            // compile-time enum constant
            return Safety.mergeAssumingUnknownIsSame(symbolAndTypeSafety, Safety.SAFE);
        }
        if (accessed.getConstValue() != null) {
            // compile-time constant value
            return Safety.mergeAssumingUnknownIsSame(symbolAndTypeSafety, Safety.SAFE);
        }
        // If a computed value was found, we can avoid expensive initializer interactions
        if (maybeFlowComputedSafety == null) {
            Safety initializer = fieldInitializerSafetyIfAvailable(accessed);
            // If the field is final, we trust the result of the initializer.
            // Non-final fields can be "tainted" by initializers (to unsafe/do-not-log) but not
            // upgraded to safe.
            if ((accessed.flags_field & Flags.FINAL) != 0
                    || initializer == Safety.DO_NOT_LOG
                    || initializer == Safety.UNSAFE) {
                return Safety.mergeAssumingUnknownIsSame(symbolAndTypeSafety, initializer);
            }
        }
        return symbolAndTypeSafety;
    }

    private Safety fieldInitializerSafetyIfAvailable(VarSymbol accessed) {
        if (!traversed.add(accessed)) {
            // Avoid infinite recursion between initializers with circular references. We recommend against
            // writing such initializers, but handle it gracefully.
            return Safety.UNKNOWN;
        }

        try {
            JavacProcessingEnvironment javacEnv = JavacProcessingEnvironment.instance(state.context);
            TreePath fieldDeclPath = Trees.instance(javacEnv).getPath(accessed);
            // Skip initializers in other compilation units as analysis of such nodes can fail due to
            // missing types.
            if (fieldDeclPath == null
                    || fieldDeclPath.getCompilationUnit() != state.getPath().getCompilationUnit()
                    || !(fieldDeclPath.getLeaf() instanceof VariableTree)) {
                return Safety.UNKNOWN;
            }

            ExpressionTree initializer = ((VariableTree) fieldDeclPath.getLeaf()).getInitializer();
            if (initializer == null) {
                return Safety.UNKNOWN;
            }

            ClassTree classTree = (ClassTree) fieldDeclPath.getParentPath().getLeaf();

            // Run flow analysis on field initializer. This is inefficient compared to just walking
            // the initializer expression tree but it avoids duplicating the logic from this transfer
            // function into a method that operates on Javac Nodes.
            TreePath initializerPath = TreePath.getPath(fieldDeclPath, initializer);
            UnderlyingAST ast = new UnderlyingAST.CFGStatement(initializerPath.getLeaf(), classTree);
            ControlFlowGraph cfg = CFGBuilder.build(initializerPath, ast, false, false, javacEnv);
            Analysis<Safety, AccessPathStore<Safety>, SafetyPropagationTransfer> analysis =
                    new ForwardAnalysisImpl<>(this);
            analysis.performAnalysis(cfg);
            Safety maybeResult = analysis.getValue(initializerPath.getLeaf());
            return maybeResult == null ? Safety.UNKNOWN : maybeResult;
        } finally {
            traversed.remove(accessed);
        }
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMethodAccess(
            MethodAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayAccess(
            ArrayAccessNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitImplicitThis(
            ImplicitThisNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Symbol symbol = ASTHelpers.getSymbol(node.getTree());
        Safety safety = symbol == null ? Safety.UNKNOWN : SafetyAnnotations.getSafety(symbol.owner, state);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitExplicitThis(
            ExplicitThisNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Symbol symbol = ASTHelpers.getSymbol(node.getTree());
        Safety safety = symbol == null ? Safety.UNKNOWN : SafetyAnnotations.getSafety(symbol.owner, state);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSuper(
            SuperNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Symbol symbol = ASTHelpers.getSymbol(node.getTree());
        Safety safety = symbol == null ? Safety.UNKNOWN : SafetyAnnotations.getSafety(symbol.owner, state);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitReturn(
            ReturnNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Node result = node.getResult();
        Safety safety = result == null ? Safety.SAFE : getValueOfSubNode(input, result);
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitLambdaResultExpression(
            LambdaResultExpressionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitStringConversion(
            StringConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = getValueOfSubNode(input, node.getOperand());
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitWideningConversion(
            WideningConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return handleTypeConversion(node.getTree(), node.getOperand(), input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitNarrowingConversion(
            NarrowingConversionNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return handleTypeConversion(node.getTree(), node.getOperand(), input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitTypeCast(
            TypeCastNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        TypeCastTree castTree = (TypeCastTree) node.getTree();
        return handleTypeConversion(castTree.getType(), node.getOperand(), input);
    }

    /** Handles type changes (widen, narrow, and cast). */
    private TransferResult<Safety, AccessPathStore<Safety>> handleTypeConversion(
            Tree newType, Node original, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety valueSafety = getValueOfSubNode(input, original);
        Type targetType = ASTHelpers.getType(newType);
        Safety narrowTargetSafety =
                targetType == null ? Safety.UNKNOWN : SafetyAnnotations.getSafety(targetType.tsym, state);
        Safety resultSafety = Safety.mergeAssumingUnknownIsSame(valueSafety, narrowTargetSafety);
        return noStoreChanges(resultSafety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitInstanceOf(
            InstanceOfNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitSynchronized(
            SynchronizedNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitAssertionError(
            AssertionErrorNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitThrow(
            ThrowNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitCase(
            CaseNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMethodInvocation(
            MethodInvocationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety methodSymbolSafety = getMethodSymbolSafety(node, input);
        Safety knownMethodSafety = getKnownMethodSafety(node, input);
        Safety result = Safety.mergeAssumingUnknownIsSame(methodSymbolSafety, knownMethodSafety);
        return noStoreChanges(result, input);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private Safety getKnownMethodSafety(
            MethodInvocationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        if (THROWABLE_GET_MESSAGE.matches(node.getTree(), state)) {
            // Auth failures are sometimes annotated '@DoNotLog', which getMessage should inherit.
            return Safety.mergeAssumingUnknownIsSame(
                    Safety.UNSAFE, getValueOfSubNode(input, node.getTarget().getReceiver()));
        } else if (RETURNS_SAFETY_OF_ARGS_AND_RECEIVER.matches(node.getTree(), state)) {
            Safety safety = getValueOfSubNode(input, node.getTarget().getReceiver());
            for (Node argument : node.getArguments()) {
                safety = safety.leastUpperBound(getValueOfSubNode(input, argument));
            }
            return safety;
        } else if (RETURNS_SAFETY_COMBINATION_OF_ARGS.matches(node.getTree(), state)) {
            Safety safety = Safety.SAFE;
            for (Node argument : node.getArguments()) {
                safety = safety.leastUpperBound(getValueOfSubNode(input, argument));
            }
            return safety;
        } else if (RETURNS_SAFETY_OF_RECEIVER.matches(node.getTree(), state)) {
            return getValueOfSubNode(input, node.getTarget().getReceiver());
        } else if (RETURNS_SAFETY_OF_FIRST_ARG.matches(node.getTree(), state)) {
            return getValueOfSubNode(input, node.getArguments().get(0));
        }
        return Safety.UNKNOWN;
    }

    private Safety getMethodSymbolSafety(
            MethodInvocationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety resultTypeSafety = SafetyAnnotations.getResultTypeSafety(node.getTree(), state);
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(node.getTree());
        if (methodSymbol != null) {
            Safety methodSafety = Safety.mergeAssumingUnknownIsSame(
                    SafetyAnnotations.getSafety(methodSymbol, state), resultTypeSafety);
            // non-annotated toString inherits type-level safety.
            if (methodSafety == Safety.UNKNOWN && TO_STRING.matches(node.getTree(), state)) {
                return getValueOfSubNode(input, node.getTarget().getReceiver());
            }
            return methodSafety;
        }
        return resultTypeSafety;
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitObjectCreation(
            ObjectCreationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety result = SafetyAnnotations.getSafety(node.getTree(), state);
        return noStoreChanges(result, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMemberReference(
            FunctionalInterfaceNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        //        Tree tree = node.getTree();
        //        if (tree instanceof LambdaExpressionTree) {
        //            LambdaExpressionTree lambdaExpression = (LambdaExpressionTree) tree;
        //            Tree body = lambdaExpression.getBody();
        //            TreePath path;
        //            if (body instanceof BlockTree) {
        //                BlockTree blockTree = (BlockTree) body;
        //                List<? extends StatementTree> statements = blockTree.getStatements();
        //                if (statements.isEmpty()) {
        //                    return unknown(input);
        //                }
        //                StatementTree finalStatement = statements.get(statements.size() - 1);
        //                if (!(finalStatement instanceof ReturnTree)) {
        //                    return unknown(input);
        //                }
        //                ReturnTree returnTree = (ReturnTree) finalStatement;
        //                path = TreePath.getPath(state.getPath().getCompilationUnit(), returnTree.getExpression());
        //            } else {
        //                path = TreePath.getPath(state.getPath().getCompilationUnit(), body);
        //            }
        //
        //            JavacProcessingEnvironment javacEnv = JavacProcessingEnvironment.instance(state.context);
        //            //            UnderlyingAST ast = new UnderlyingAST.CFGMethod(
        //            //                    ASTHelpers.findEnclosingNode(path, MethodTree.class),
        //            //                    ASTHelpers.findEnclosingNode(path, ClassTree.class));
        //            UnderlyingAST ast = new UnderlyingAST.CFGLambda(
        //                    lambdaExpression,
        //                    ASTHelpers.findEnclosingNode(path, ClassTree.class),
        //                    ASTHelpers.findEnclosingNode(path, MethodTree.class));
        //            ControlFlowGraph cfg = CFGBuilder.build(path, ast, false, false, javacEnv);
        //            input.getRegularStore();
        //            Analysis<Safety, AccessPathStore<Safety>, SafetyPropagationTransfer> analysis =
        //                    new ForwardAnalysisImpl<>(withSafety(
        //                            lambdaExpression.getParameters().stream()
        //                                    .map(ASTHelpers::getSymbol)
        //                                    .collect(ImmutableMap.toImmutableMap(sym -> sym, _ignored ->
        // Safety.DO_NOT_LOG)),
        //                            input.getRegularStore()));
        //            //            additionalData.put(AccessPath.fromNodeIfTrackable())
        //            analysis.performAnalysis(cfg);
        //            Safety maybeResult = analysis.getValue(path.getLeaf());
        //            Safety safety = maybeResult == null ? Safety.UNKNOWN : maybeResult;
        //            return noStoreChanges(safety, input);
        //        } else if (tree instanceof MemberReferenceTree) {
        //            // Determine safety of the referenced method based on annotations and result type
        //            return unknown(input);
        //        } else {
        //            throw new UnsupportedOperationException(
        //                    "Safety analysis bug, FunctionalInterfaceNode tree type: " + tree.getClass() + " for node
        // " + node);
        //        }
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayCreation(
            ArrayCreationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        Safety safety = Safety.SAFE;
        for (Node item : node.getInitializers()) {
            safety = safety.leastUpperBound(getValueOfSubNode(input, item));
        }
        return noStoreChanges(safety, input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitArrayType(
            ArrayTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitPrimitiveType(
            PrimitiveTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitClassName(
            ClassNameNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitPackageName(
            PackageNameNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitParameterizedType(
            ParameterizedTypeNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitMarker(
            MarkerNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    @Override
    public TransferResult<Safety, AccessPathStore<Safety>> visitClassDeclaration(
            ClassDeclarationNode node, TransferInput<Safety, AccessPathStore<Safety>> input) {
        return unknown(input);
    }

    /**
     * Equivalent to {@link TransferInput#getValueOfSubNode(Node)},
     * but returning {@link Safety#UNKNOWN} rather than null.
     */
    private static Safety getValueOfSubNode(TransferInput<Safety, AccessPathStore<Safety>> input, Node node) {
        Safety maybeSafety = input.getValueOfSubNode(node);
        return Safety.nullToUnknown(maybeSafety);
    }
}
