/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.classuniqueness;

import java.io.Serializable;

/**
 * How the lock file shows a jar: {@code group:name}, followed by {@code  (classifier=...)} if it has one, such as
 * {@code g:bar (classifier=tests)}. It also identifies the jar: jars with the same label count as one.
 */
public record JarLabel(String value) implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return value;
    }
}
