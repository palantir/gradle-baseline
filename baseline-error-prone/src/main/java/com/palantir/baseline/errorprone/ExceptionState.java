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

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.checkerframework.errorprone.dataflow.analysis.AbstractValue;

/**
 * Represents the set of EndpointServiceException subtypes that can be thrown from a program point.
 */
public final class ExceptionState implements AbstractValue<ExceptionState> {

    /**
     * No EndpointServiceException subtypes can be thrown from this point.
     */
    public static final ExceptionState NO_EXCEPTION = new ExceptionState(ImmutableSet.of());

    private final ImmutableSet<String> thrownExceptions;

    private ExceptionState(Set<String> thrownExceptions) {
        this.thrownExceptions = ImmutableSet.copyOf(thrownExceptions);
    }

    /**
     * Creates an ExceptionState with a single exception type.
     */
    public static ExceptionState withException(String exceptionName) {
        return new ExceptionState(ImmutableSet.of(exceptionName));
    }

    /**
     * Creates an ExceptionState with multiple exception types.
     */
    public static ExceptionState withExceptions(Set<String> exceptionNames) {
        return exceptionNames.isEmpty() ? NO_EXCEPTION : new ExceptionState(exceptionNames);
    }

    /**
     * Returns the set of exception names that can be thrown.
     */
    public ImmutableSet<String> getThrownExceptions() {
        return thrownExceptions;
    }

    /**
     * Returns true if any EndpointServiceException subtypes can be thrown.
     */
    public boolean canThrowEndpointException() {
        return !thrownExceptions.isEmpty();
    }

    @Override
    public ExceptionState leastUpperBound(ExceptionState other) {
        if (other == null) {
            return this;
        }

        // Union of both sets of exceptions
        ImmutableSet<String> combined = ImmutableSet.<String>builder()
                .addAll(this.thrownExceptions)
                .addAll(other.thrownExceptions)
                .build();

        return combined.isEmpty() ? NO_EXCEPTION : new ExceptionState(combined);
    }

    @Override
    public String toString() {
        return thrownExceptions.isEmpty() ? "NO_EXCEPTION" : "EXCEPTIONS: " + thrownExceptions;
    }

    /**
     * Converts null to NO_EXCEPTION.
     */
    public static ExceptionState nullToNoException(ExceptionState input) {
        return input == null ? NO_EXCEPTION : input;
    }
}
