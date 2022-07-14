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

package com.palantir.baseline.plugins.javaversions;

public enum EnablePreview {
    ENABLE_PREVIEW,
    DEFAULT_OFF;

    public static final String SUFFIX = "_PREVIEW";
    public static final String FLAG = "--enable-preview";
    public static final String JAVADOC_FLAG = "-enable-preview";

    static EnablePreview fromString(String string) {
        return string.endsWith(SUFFIX) ? ENABLE_PREVIEW : DEFAULT_OFF;
    }
}
