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

    /*
     * Do not add SUGGESTION checks here. Instead either increase the severity to WARNING or do not apply them by
     * default.
     */
    private static final ImmutableList<String> DEFAULT_PATCH_CHECKS = ImmutableList.of(
            // Baseline checks
            "BracesRequired",
            "CatchBlockLogException",
            // TODO(ckozak): re-enable pending scala check
            // "CatchSpecificity",
            "CollectionStreamForEach",
            "ExecutorSubmitRunnableFutureIgnored",
            "ExtendsErrorOrThrowable",
            "FinalClass",
            "ImplicitPublicBuilderConstructor",
            "LambdaMethodReference",
            "LoggerEnclosingClass",
            "LogsafeArgName",
            "OptionalFlatMapOfNullable",
            "OptionalOrElseMethodInvocation",
            "PreferBuiltInConcurrentKeySet",
            "PreferCollectionConstructors",
            "PreferCollectionTransform",
            "PreferListsPartition",
            "PreferSafeLoggableExceptions",
            "PreferSafeLoggingPreconditions",
            "PreferStaticLoggers",
            "PublicConstructorForAbstractClass",
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
            "UnnecessarilyQualified",
            "UnnecessaryLambdaArgumentParentheses",
            // TODO(ckozak): re-enable pending scala check
            // "ThrowSpecificity",
            "UnsafeGaugeRegistration",

            // Built-in checks
            "ArrayEquals",
            "BadImport",
            "MissingOverride",
            "UnnecessaryParentheses",
            "PreferJavaTimeOverload",
            "ProtectedMembersInFinalClass");

    private final ListProperty<String> patchChecks;

    public BaselineErrorProneExtension(Project project) {
        patchChecks = project.getObjects().listProperty(String.class);
        patchChecks.set(DEFAULT_PATCH_CHECKS);
    }

    public final ListProperty<String> getPatchChecks() {
        return patchChecks;
    }
}
