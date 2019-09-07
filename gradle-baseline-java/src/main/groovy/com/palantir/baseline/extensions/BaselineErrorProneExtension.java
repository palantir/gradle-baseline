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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugCheckerInfo;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.options.Option;

public class BaselineErrorProneExtension {

    private static final ImmutableList<String> DEFAULT_PATCH_CHECKS = ImmutableList.of(
            // Baseline checks
            "LambdaMethodReference",
            "OptionalOrElseMethodInvocation",
            "PreferBuiltInConcurrentKeySet",
            "PreferCollectionTransform",
            "PreferListsPartition",
            "PreferSafeLoggableExceptions",
            "PreferSafeLoggingPreconditions",

            // Built-in checks
            "ArrayEquals",
            "MissingOverride");

    private static final ImmutableSet<String> DEFAULT_DISABLED_CHECKS = ImmutableSet.of(
            "AndroidJdkLibsChecker", // ignore Android
            "Java7ApiChecker", // we require JDK8+
            "StaticOrDefaultInterfaceMethod", // Android specific
            "Var" // high noise, low signal
    );

    private final ListProperty<String> patchChecks;

    private final SetProperty<String> disabledChecks;

    private final Property<Boolean> isStrict;

    public BaselineErrorProneExtension(Project project) {
        patchChecks = project.getObjects().listProperty(String.class);
        patchChecks.set(DEFAULT_PATCH_CHECKS);
        disabledChecks = project.getObjects().setProperty(String.class);
        disabledChecks.set(DEFAULT_DISABLED_CHECKS);
        isStrict = project.getObjects().property(Boolean.class);
        isStrict.set(false);
    }

    public final ListProperty<String> getPatchChecks() {
        return patchChecks;
    }

    public final boolean isStrict() {
        return Boolean.TRUE.equals(isStrict.get());
    }

    @Option(option = "strict", description = "Whether to apply strict compilation checks")
    public final void strict(boolean shouldEnableStrict) {
        this.isStrict.set(shouldEnableStrict);
    }

    public final void strict(Provider<Boolean> shouldEnableStrict) {
        this.isStrict.set(shouldEnableStrict);
    }

    public final boolean isCheckEnabled(BugCheckerInfo check) {
        return isCheckEnabled(check.canonicalName());
    }

    public final boolean isCheckEnabled(String check) {
        return !disabledChecks.get().contains(check);
    }

    @Option(option = "disableCheck", description = "Disable specific error-prone check")
    public final void disableCheck(String check) {
        this.disabledChecks.add(check);
    }
}
