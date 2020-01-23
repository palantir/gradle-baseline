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
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;

public class BaselineErrorProneExtension {
    private static final ImmutableList<String> DEFAULT_PATCH_CHECKS = ImmutableList.of(
            // Baseline checks
            "BracesRequired",
            "CatchBlockLogException",
            "CollectionStreamForEach",
            // TODO(ckozak): re-enable pending scala check
            // "CatchSpecificity",
            "ExecutorSubmitRunnableFutureIgnored",
            "FinalClass",
            "LambdaMethodReference",
            "LoggerEnclosingClass",
            "OptionalOrElseMethodInvocation",
            "PreferBuiltInConcurrentKeySet",
            "PreferCollectionTransform",
            "PreferListsPartition",
            "PreferSafeLoggableExceptions",
            "PreferSafeLoggingPreconditions",
            "ReadReturnValueIgnored",
            "RedundantMethodReference",
            "RedundantModifier",
            "Slf4jLevelCheck",
            "Slf4jLogsafeArgs",
            "Slf4jThrowable",
            "StreamOfEmpty",
            "StrictUnusedVariable",
            "StringBuilderConstantParameters",
            "ThrowError",
            // TODO(ckozak): re-enable pending scala check
            // "ThrowSpecificity",
            "UnsafeGaugeRegistration",

            // Built-in checks
            "ArrayEquals",
            "MissingOverride",
            "UnnecessaryParentheses",
            "PreferJavaTimeOverload");

    private final ListProperty<String> patchChecks;

    public BaselineErrorProneExtension(Project project) {
        patchChecks = project.getObjects().listProperty(String.class);
        patchChecks.set(DEFAULT_PATCH_CHECKS);
    }

    public final ListProperty<String> getPatchChecks() {
        return patchChecks;
    }
}
