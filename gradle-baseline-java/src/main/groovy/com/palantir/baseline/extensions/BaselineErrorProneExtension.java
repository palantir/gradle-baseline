/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.extensions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;

public class BaselineErrorProneExtension {
    private static final Logger log = Logging.getLogger(BaselineErrorProneExtension.class);

    private static final ImmutableList<String> DEFAULT_PATCH_CHECKS = ImmutableList.of(
            // Baseline checks
            "ExecutorSubmitRunnableFutureIgnored",
            "LambdaMethodReference",
            "OptionalOrElseMethodInvocation",
            "PreferBuiltInConcurrentKeySet",
            "PreferCollectionTransform",
            "PreferListsPartition",
            "PreferSafeLoggableExceptions",
            "PreferSafeLoggingPreconditions",
            "Slf4jLevelCheck",
            "StrictUnusedVariable",
            "StringBuilderConstantParameters",
            "ThrowError",

            // Built-in checks
            "ArrayEquals",
            "MissingOverride",
            "UnnecessaryParentheses");

    private final ListProperty<String> patchChecks;

    public BaselineErrorProneExtension(Project project) {
        patchChecks = project.getObjects().listProperty(String.class);
        patchChecks.set(DEFAULT_PATCH_CHECKS);
    }

    public final ListProperty<String> getPatchChecks() {
        return patchChecks;
    }

    /**
     * Filters down the patch checks, removing checks that depend on certain libraries being present on the compile
     * class path.
     */
    public final List<String> getFilteredPatchChecks(FileCollection compileClasspath) {
        boolean hasSafeLogging = !compileClasspath.filter(file -> file.getName().startsWith("safe-logging-")).isEmpty();
        boolean hasPreconditions =
                // The real 'preconditions' brings in 'safe-logging'. Because of inaccurate jar name checks, we
                // use that fact to ensure we're not picking up another jar named 'preconditions' by mistake.
                hasSafeLogging
                        && !compileClasspath.filter(file -> file.getName().startsWith("preconditions-")).isEmpty();
        Stream<String> checksStream = patchChecks.get().stream();
        if (!hasPreconditions) {
            checksStream = disableCheck(compileClasspath, checksStream, "PreferSafeLoggingPreconditions");
        }
        if (!hasSafeLogging) {
            checksStream = disableCheck(compileClasspath, checksStream, "PreferSafeLoggableExceptions");
        }
        return checksStream.collect(Collectors.toList());
    }

    @CheckReturnValue
    private static Stream<String> disableCheck(
            FileCollection compileClasspath, Stream<String> checksStream, String checkName) {
        log.info("Disabling check " + checkName + " as library missing from source set for {}", compileClasspath);
        return checksStream.filter(check -> !check.equals(checkName));
    }
}
