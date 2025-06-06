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
import com.google.errorprone.dataflow.DataFlow;
import com.palantir.baseline.errorprone.ExceptionPropagationTransfer.ClearVisitorState;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.util.Context;
import java.util.Set;

/**
 * Helper class for analyzing exception propagation using dataflow analysis.
 * Similar to SafetyAnalysis but for EndpointServiceException tracking.
 */
public final class ExceptionAnalysis {
    private static final Context.Key<ExceptionPropagationTransfer> EXCEPTION_PROPAGATION = new Context.Key<>();

    /**
     * Returns whether EndpointServiceException subtypes can be thrown from the current path.
     * Callers may need to use {@link VisitorState#withPath(TreePath)} to provide a more specific path.
     */
    public static boolean canThrowEndpointException(VisitorState state) {
        ExceptionPropagationTransfer propagation = instance(state.context);
        try (ClearVisitorState ignored = propagation.setVisitorState(state)) {
            ExceptionState res = DataFlow.expressionDataflow(state.getPath(), state.context, propagation);
            ExceptionState result = ExceptionState.nullToNoException(res);
            return result.canThrowEndpointException();
        }
    }

    /**
     * Returns the set of EndpointServiceException subtype names that can be thrown from the current path.
     * Callers may need to use {@link VisitorState#withPath(TreePath)} to provide a more specific path.
     */
    public static Set<ClassSymbol> getThrownExceptionNames(VisitorState state) {
        ExceptionPropagationTransfer propagation = instance(state.context);
        try (ClearVisitorState ignored = propagation.setVisitorState(state)) {
            ExceptionState res = DataFlow.expressionDataflow(state.getPath(), state.context, propagation);
            ExceptionState result = ExceptionState.nullToNoException(res);
            return result.getThrownExceptions();
        }
    }

    private static ExceptionPropagationTransfer instance(Context context) {
        ExceptionPropagationTransfer instance = context.get(EXCEPTION_PROPAGATION);
        if (instance == null) {
            instance = new ExceptionPropagationTransfer();
            context.put(EXCEPTION_PROPAGATION, instance);
        }
        return instance;
    }

    private ExceptionAnalysis() {}
}
