/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.util;

import com.google.common.base.Splitter;
import org.gradle.api.GradleException;

public final class VersionUtils {
    public static int majorVersionNumber(String version) {
        return Integer.parseInt(Splitter.on('.')
                .splitToStream(version)
                .findFirst()
                .orElseThrow(() -> new GradleException("Cannot find major version number for version " + version)));
    }

    private VersionUtils() {}
}
