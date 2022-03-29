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
import com.sun.tools.javac.util.Context;

public final class SafetyAnalysis {
    private static final Context.Key<SafetyAnalysis> SAFETY_ANALYSIS_KEY = new Context.Key<>();
    private final SafetyPropagationTransfer safetyPropagation = new SafetyPropagationTransfer();

    public static SafetyAnalysis instance(Context context) {
        SafetyAnalysis instance = context.get(SAFETY_ANALYSIS_KEY);
        if (instance == null) {
            instance = new SafetyAnalysis();
            context.put(SAFETY_ANALYSIS_KEY, instance);
        }
        return instance;
    }

    private SafetyAnalysis() {}

    public Safety getSafety(VisitorState state) {
        try (ClearVisitorState ignored = safetyPropagation.setVisitorState(state)) {
            return DataFlow.expressionDataflow(state.getPath(), state.context, safetyPropagation);
        }
    }
}
