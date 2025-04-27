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

package com.palantir.gradle.testing.files.java;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaSourceUtils {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([^;]+);");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:class|interface|record|enum)\\s+(\\w+)");

    static Optional<String> extractPackage(String source) {
        return possiblyExtractGroup(PACKAGE_PATTERN, source);
    }

    static Optional<String> extractClassName(String source) {
        return possiblyExtractGroup(CLASS_PATTERN, source);
    }

    private static Optional<String> possiblyExtractGroup(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private JavaSourceUtils() {}
}
