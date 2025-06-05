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

import org.checkerframework.errorprone.dataflow.analysis.AbstractValue;

/**
 * Represents whether EndpointServiceException subtypes can be thrown from a program point.
 */
public enum ExceptionState implements AbstractValue<ExceptionState> {
    /**
     * No EndpointServiceException subtypes can be thrown from this point.
     */
    NO_EXCEPTION() {
        @Override
        public ExceptionState leastUpperBound(ExceptionState other) {
            return nullToNoException(other);
        }
    },
    /**
     * EndpointServiceException subtypes can be thrown from this point.
     */
    CAN_THROW_ENDPOINT_EXCEPTION() {
        @Override
        public ExceptionState leastUpperBound(ExceptionState other) {
            // If either path can throw, the merged result can throw
            return this;
        }
    };

    public boolean canThrowEndpointException() {
        return this == CAN_THROW_ENDPOINT_EXCEPTION;
    }

    static ExceptionState nullToNoException(ExceptionState input) {
        return input == null ? NO_EXCEPTION : input;
    }
}
