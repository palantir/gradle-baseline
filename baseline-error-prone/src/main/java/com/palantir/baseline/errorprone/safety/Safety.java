/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.errorprone.safety;

public enum Safety {
    UNKNOWN() {
        @Override
        public boolean allowsValueWith(Safety _valueSafety) {
            // No constraints when safety isn't specified
            return true;
        }
    },
    DO_NOT_LOG() {
        @Override
        public boolean allowsValueWith(Safety _valueSafety) {
            // do-not-log on a parameter isn't meaningful for callers, only for the implementation
            return true;
        }
    },
    UNSAFE() {
        @Override
        public boolean allowsValueWith(Safety valueSafety) {
            // We allow safe data to be provided to an unsafe annotated parameter because that's safe, however
            // we should separately flag and prompt migration of such UnsafeArgs to SafeArg.
            return valueSafety != Safety.DO_NOT_LOG;
        }
    },
    SAFE() {
        @Override
        public boolean allowsValueWith(Safety valueSafety) {
            return valueSafety == Safety.UNKNOWN || valueSafety == Safety.SAFE;
        }
    };

    public abstract boolean allowsValueWith(Safety valueSafety);
}