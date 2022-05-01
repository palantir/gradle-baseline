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

import com.google.common.collect.Multimap;
import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

public final class SafetyAnnotations {
    public static final String SAFE = "com.palantir.logsafe.Safe";
    public static final String UNSAFE = "com.palantir.logsafe.Unsafe";
    public static final String DO_NOT_LOG = "com.palantir.logsafe.DoNotLog";

    private static final com.google.errorprone.suppliers.Supplier<Type> throwableSupplier =
            Suppliers.typeFromClass(Throwable.class);

    private static final TypeArgumentHandlers SAFETY_IS_COMBINATION_OF_TYPE_ARGUMENTS = new TypeArgumentHandlers(
            new TypeArgumentHandler(Iterable.class),
            new TypeArgumentHandler(Iterator.class),
            new TypeArgumentHandler(Map.class),
            new TypeArgumentHandler(Map.Entry.class),
            new TypeArgumentHandler(Multimap.class),
            new TypeArgumentHandler(Stream.class),
            new TypeArgumentHandler(Optional.class));

    public static Safety getSafety(Tree tree, VisitorState state) {
        // Check the symbol itself:
        Symbol treeSymbol = ASTHelpers.getSymbol(tree);
        Safety symbolSafety = getSafety(treeSymbol, state);
        Type treeType = tree instanceof ExpressionTree
                ? ASTHelpers.getResultType((ExpressionTree) tree)
                : ASTHelpers.getType(tree);
        Safety treeTypeSafety = treeType == null
                ? Safety.UNKNOWN
                : Safety.mergeAssumingUnknownIsSame(getSafety(treeType, state), getSafety(treeType.tsym, state));
        Type symbolType = treeSymbol == null ? null : treeSymbol.type;
        // If the type extracted from the symbol matches the type extracted from the tree, avoid duplicate work.
        // However, in some cases type parameter information is excluded from one, but not the other.
        Safety symbolTypeSafety = symbolType == null
                        || (treeType != null && state.getTypes().isSameType(treeType, symbolType))
                ? Safety.UNKNOWN
                : Safety.mergeAssumingUnknownIsSame(getSafety(symbolType, state), getSafety(symbolType.tsym, state));
        return Safety.mergeAssumingUnknownIsSame(symbolSafety, treeTypeSafety, symbolTypeSafety);
    }

    public static Safety getSafety(@Nullable Symbol symbol, VisitorState state) {
        if (symbol != null) {
            if (ASTHelpers.hasAnnotation(symbol, DO_NOT_LOG, state)) {
                return Safety.DO_NOT_LOG;
            }
            if (ASTHelpers.hasAnnotation(symbol, UNSAFE, state)) {
                return Safety.UNSAFE;
            }
            if (ASTHelpers.hasAnnotation(symbol, SAFE, state)) {
                return Safety.SAFE;
            }
            // Check super-methods
            if (symbol instanceof MethodSymbol) {
                return getSuperMethodSafety((MethodSymbol) symbol, state);
            }
            if (symbol instanceof VarSymbol) {
                VarSymbol varSymbol = (VarSymbol) symbol;
                return getSuperMethodParameterSafety(varSymbol, state);
            }
        }
        return Safety.UNKNOWN;
    }

    public static Safety getSafety(@Nullable Type type, VisitorState state) {
        if (type != null) {
            return getSafetyInternal(type, state, null);
        }
        return Safety.UNKNOWN;
    }

    private static Safety getSafetyInternal(Type type, VisitorState state, Set<String> dejaVu) {
        List<Attribute.TypeCompound> typeAnnotations = type.getAnnotationMirrors();
        for (Attribute.TypeCompound annotation : typeAnnotations) {
            TypeElement annotationElement =
                    (TypeElement) annotation.getAnnotationType().asElement();
            Name name = annotationElement.getQualifiedName();
            if (name.contentEquals(DO_NOT_LOG)) {
                return Safety.DO_NOT_LOG;
            }
            if (name.contentEquals(UNSAFE)) {
                return Safety.UNSAFE;
            }
            if (name.contentEquals(SAFE)) {
                return Safety.SAFE;
            }
        }
        Safety typeArgumentCombination = SAFETY_IS_COMBINATION_OF_TYPE_ARGUMENTS.getSafety(type, state, dejaVu);
        return ASTHelpers.isSubtype(type, throwableSupplier.get(state), state)
                ? Safety.UNSAFE.leastUpperBound(typeArgumentCombination)
                : typeArgumentCombination;
    }

    private static final class TypeArgumentHandlers {
        private final TypeArgumentHandler[] handlers;

        TypeArgumentHandlers(TypeArgumentHandler... handlers) {
            this.handlers = handlers;
        }

        Safety getSafety(Type type, VisitorState state, @Nullable Set<String> dejaVu) {
            for (TypeArgumentHandler handler : handlers) {
                Safety result = handler.getSafety(type, state, dejaVu);
                if (result != null) {
                    return result;
                }
            }
            return Safety.UNKNOWN;
        }
    }

    private static final class TypeArgumentHandler {
        private final com.google.errorprone.suppliers.Supplier<Type> typeSupplier;

        TypeArgumentHandler(Class<?> clazz) {
            if (clazz.getTypeParameters().length == 0) {
                throw new IllegalStateException("Class " + clazz + " has no type parameters");
            }
            this.typeSupplier = Suppliers.typeFromClass(clazz);
        }

        private static Type unwrapWildcard(Type type) {
            if (type instanceof WildcardType) {
                WildcardType wildcard = (WildcardType) type;
                Type ext = wildcard.getExtendsBound();
                if (ext != null) {
                    return ext;
                }
            }
            return type;
        }

        @Nullable
        Safety getSafety(Type type, VisitorState state, @Nullable Set<String> dejaVu) {
            Type baseType = typeSupplier.get(state);
            if (ASTHelpers.isSubtype(type, baseType, state)) {
                // ensure we're matching the expected type arguments
                Set<String> deJaVuToPass = dejaVu == null ? new HashSet<>() : dejaVu;
                // Use the string value for cycle detection, the type itself is not guaranteed
                // to declare hash/equals.
                String typeString = type.toString();
                if (!deJaVuToPass.add(typeString)) {
                    return Safety.UNKNOWN;
                }
                // Apply the input type arguments to the base type
                Type asSubtype = state.getTypes().asSuper(unwrapWildcard(type), baseType.tsym);
                if (asSubtype == null) {
                    // Some types cannot be bound to a super-type. We attempt to unwrap wildcards, however
                    // that doesn't cover every possible case.
                    return null;
                }
                Safety safety = Safety.SAFE;
                List<Type> typeArguments = asSubtype.getTypeArguments();
                if (typeArguments.isEmpty()) {
                    // Type information is not available, not enough data to make a decision
                    return null;
                }
                for (Type typeArgument : typeArguments) {
                    Safety safetyBasedOnType = SafetyAnnotations.getSafetyInternal(typeArgument, state, deJaVuToPass);
                    Safety safetyBasedOnSymbol = SafetyAnnotations.getSafety(typeArgument.tsym, state);
                    Safety typeArgumentSafety =
                            Safety.mergeAssumingUnknownIsSame(safetyBasedOnType, safetyBasedOnSymbol);
                    safety = safety.leastUpperBound(typeArgumentSafety);
                }
                // remove the type on the way out, otherwise map<Foo,Foo> would break.
                deJaVuToPass.remove(typeString);
                return safety;
            }
            return null;
        }
    }

    private static Safety getSuperMethodSafety(MethodSymbol method, VisitorState state) {
        Safety safety = Safety.UNKNOWN;
        if (!method.isStaticOrInstanceInit()) {
            for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(method, state.getTypes())) {
                safety = Safety.mergeAssumingUnknownIsSame(safety, getSafety(superMethod, state));
            }
        }
        return safety;
    }

    private static Safety getSuperMethodParameterSafety(VarSymbol varSymbol, VisitorState state) {
        Safety safety = Safety.UNKNOWN;
        if (varSymbol.owner instanceof MethodSymbol) {
            // If the owner is a MethodSymbol, this variable is a method parameter
            MethodSymbol method = (MethodSymbol) varSymbol.owner;
            if (!method.isStaticOrInstanceInit()) {
                List<VarSymbol> methodParameters = method.getParameters();
                for (int i = 0; i < methodParameters.size(); i++) {
                    VarSymbol current = methodParameters.get(i);
                    if (Objects.equals(current, varSymbol)) {
                        for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(method, state.getTypes())) {
                            safety = Safety.mergeAssumingUnknownIsSame(
                                    safety,
                                    getSafety(superMethod.getParameters().get(i), state));
                        }
                        return safety;
                    }
                }
            }
        }
        return safety;
    }

    private SafetyAnnotations() {}
}
