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

import org.checkerframework.errorprone.dataflow.analysis.AbstractValue;

public enum Safety implements AbstractValue<Safety> {
    UNKNOWN() {
        @Override
        public Safety leastUpperBound(Safety other) {
            return other == SAFE ? this : nullToUnknown(other);
        }

        @Override
        public boolean allowsValueWith(Safety _valueSafety) {
            // No constraints when safety isn't specified
            return true;
        }
    },
    DO_NOT_LOG() {
        @Override
        public Safety leastUpperBound(Safety _other) {
            return this;
        }

        @Override
        public boolean allowsValueWith(Safety _valueSafety) {
            // do-not-log on a parameter isn't meaningful for callers, only for the implementation
            return true;
        }
    },
    UNSAFE() {
        @Override
        public Safety leastUpperBound(Safety other) {
            return other == DO_NOT_LOG ? other : this;
        }

        @Override
        public boolean allowsValueWith(Safety valueSafety) {
            // We allow safe data to be provided to an unsafe annotated parameter because that's safe, however
            // we should separately flag and prompt migration of such UnsafeArgs to SafeArg.
            return nullToUnknown(valueSafety) != Safety.DO_NOT_LOG;
        }
    },
    SAFE() {
        @Override
        public Safety leastUpperBound(Safety other) {
            return nullToUnknown(other);
        }

        @Override
        public boolean allowsValueWith(Safety valueSafety) {
            return nullToUnknown(valueSafety) == Safety.UNKNOWN || valueSafety == Safety.SAFE;
        }
    };

    public abstract boolean allowsValueWith(Safety valueSafety);

    public boolean allowsAll() {
        return this == UNKNOWN || this == DO_NOT_LOG;
    }

    /**
     * Merge Safety using {@link Safety#leastUpperBound(AbstractValue)} except that {@link Safety#UNKNOWN} assumes
     * no confidence, preferring the other type if data is available.
     * For example, casting from {@link Object} to a known-safe type should result in {@link Safety#SAFE}.
     */
    public static Safety mergeAssumingUnknownIsSame(Safety first, Safety second) {
        Safety one = nullToUnknown(first);
        Safety two = nullToUnknown(second);
        if (one == UNKNOWN) {
            return two;
        }
        if (two == UNKNOWN) {
            return one;
        }
        return one.leastUpperBound(two);
    }

    public static Safety mergeAssumingUnknownIsSame(Safety one, Safety two, Safety three) {
        Safety result = mergeAssumingUnknownIsSame(one, two);
        return mergeAssumingUnknownIsSame(result, nullToUnknown(three));
    }

    static Safety nullToUnknown(Safety input) {
        return input == null ? Safety.UNKNOWN : input;
    }
}
