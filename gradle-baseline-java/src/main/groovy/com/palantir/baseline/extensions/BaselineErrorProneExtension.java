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
import org.gradle.api.provider.ListProperty;

public abstract class BaselineErrorProneExtension {

    /*
     * Do not add SUGGESTION checks here. Instead either increase the severity to WARNING or do not apply them by
     * default.
     */
    private static final ImmutableList<String> DEFAULT_PATCH_CHECKS = ImmutableList.of(
            // Baseline checks
            "AssertNoArgs",
            "AvoidNewHashMapInt",
            "BugCheckerAutoService",
            "CatchBlockLogException",
            // TODO(ckozak): re-enable pending scala check
            // "CatchSpecificity",
            "CollectionStreamForEach",
            "CompileTimeConstantViolatesLiskovSubstitution",
            "ConsistentLoggerName",
            "ConsistentOverrides",
            "DeprecatedGuavaObjects",
            "ExecutorSubmitRunnableFutureIgnored",
            "ExtendsErrorOrThrowable",
            "FinalClass",
            "IllegalSafeLoggingArgument",
            "ImmutableMapDuplicateKeyStrategy",
            "ImmutablesStyle",
            "ImplicitPublicBuilderConstructor",
            "JavaTimeDefaultTimeZone",
            "JavaTimeSystemDefaultTimeZone",
            "LambdaMethodReference",
            "LoggerEnclosingClass",
            "LogsafeArgName",
            "ObjectsHashCodeUnnecessaryVarargs",
            "OptionalFlatMapOfNullable",
            "OptionalOrElseMethodInvocation",
            "PreferBuiltInConcurrentKeySet",
            "PreferCollectionConstructors",
            "PreferCollectionTransform",
            "PreferInputStreamTransferTo",
            "PreferListsPartition",
            "PreferSafeLogger",
            "PreferStaticLoggers",
            "ProxyNonConstantType",
            "ReadReturnValueIgnored",
            "RedundantMethodReference",
            "RedundantModifier",
            "SafeLoggingPropagation",
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
            "VarUsage",
            "ZeroWarmupRateLimiter",
            "ZoneIdConstant",

            // Built-in checks
            "ArrayEquals",
            "BadImport",
            "LongDoubleConversion",
            "LoopOverCharArray",
            "MissingBraces",
            "MissingOverride",
            "NarrowCalculation",
            "ObjectsHashCodePrimitive",
            "ProtectedMembersInFinalClass",
            "UnnecessaryParentheses",
            "ZoneIdOfZ");

    public abstract ListProperty<String> getPatchChecks();

    public BaselineErrorProneExtension() {
        getPatchChecks().addAll(DEFAULT_PATCH_CHECKS);
    }
}
