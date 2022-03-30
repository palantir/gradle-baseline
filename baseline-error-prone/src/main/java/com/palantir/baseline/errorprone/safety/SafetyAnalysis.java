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
import com.google.errorprone.dataflow.DataFlow;
import com.palantir.baseline.errorprone.safety.SafetyPropagationTransfer.ClearVisitorState;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.util.Context;

public final class SafetyAnalysis {
    private static final Context.Key<SafetyPropagationTransfer> SAFETY_PROPAGATION = new Context.Key<>();

    /**
     * Returns the safety of the item at the current path.
     * Callers may need to use {@link VisitorState#withPath(TreePath)} to provide a more specific path.
     */
    public static Safety of(VisitorState state) {
        SafetyPropagationTransfer propagation = instance(state.context);
        try (ClearVisitorState ignored = propagation.setVisitorState(state)) {
            return DataFlow.expressionDataflow(state.getPath(), state.context, propagation);
        }
    }

    private static SafetyPropagationTransfer instance(Context context) {
        SafetyPropagationTransfer instance = context.get(SAFETY_PROPAGATION);
        if (instance == null) {
            instance = new SafetyPropagationTransfer();
            context.put(SAFETY_PROPAGATION, instance);
        }
        return instance;
    }

    private SafetyAnalysis() {}
}
