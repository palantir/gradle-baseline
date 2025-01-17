/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins;

import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.gradle.suppressibleerrorprone.ConditionalPatchCheck;
import com.palantir.gradle.suppressibleerrorprone.IfModuleIsUsed;
import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorProneExtension;
import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorPronePlugin;
import java.util.Optional;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class BaselineErrorProne implements Plugin<Project> {
    public static final String EXTENSION_NAME = "baselineErrorProne";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);
        SuppressibleErrorProneExtension suppressibleErrorProneExtension =
                project.getExtensions().getByType(SuppressibleErrorProneExtension.class);

        project.getExtensions()
                .create(EXTENSION_NAME, BaselineErrorProneExtension.class, suppressibleErrorProneExtension);

        String version = Optional.ofNullable((String) project.findProperty("baselineErrorProneVersion"))
                .or(() -> Optional.ofNullable(
                        BaselineErrorProne.class.getPackage().getImplementationVersion()))
                .orElseThrow(() -> new RuntimeException("BaselineErrorProne implementation version not found"));

        project.getDependencies()
                .add(ErrorPronePlugin.CONFIGURATION_NAME, "com.palantir.baseline:baseline-error-prone:" + version);

        suppressibleErrorProneExtension
                .getConditionalPatchChecks()
                .addAll(
                        new ConditionalPatchCheck(
                                new IfModuleIsUsed("com.palantir.safe-logging", "preconditions"),
                                "PreferSafeLoggingPreconditions",
                                "PreferSafeLoggableExceptions"),
                        new ConditionalPatchCheck(
                                new IfModuleIsUsed("com.palantir.safe-logging", "logger"), "PreferSafeLogger"));

        suppressibleErrorProneExtension.configureEachErrorProneOptions(BaselineErrorProne::configureErrorProneOptions);

        project.getPluginManager().withPlugin("java-gradle-plugin", appliedPlugin -> {
            suppressibleErrorProneExtension.configureEachErrorProneOptions(errorProneOptions -> {
                errorProneOptions.disable("CatchBlockLogException");
                errorProneOptions.disable("JavaxInjectOnAbstractMethod");
                errorProneOptions.disable("PreconditionsConstantMessage");
                errorProneOptions.disable("PreferSafeLoggableExceptions");
                errorProneOptions.disable("PreferSafeLogger");
                errorProneOptions.disable("PreferSafeLoggingPreconditions");
                errorProneOptions.disable("Slf4jConstantLogMessage");
                errorProneOptions.disable("Slf4jLogsafeArgs");
                errorProneOptions.disable("InjectOnConstructorOfAbstractClass");
            });
        });

        project.getPluginManager().withPlugin("org.jetbrains.intellij", appliedPlugin -> {
            suppressibleErrorProneExtension.configureEachErrorProneOptions(errorProneOptions -> {
                errorProneOptions.disable("PreferSafeLogger");
                errorProneOptions.disable("PreferSafeLoggableExceptions");
                errorProneOptions.disable("PreferSafeLoggingPreconditions");
                errorProneOptions.disable("StrictUnusedVariable");
            });
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void configureErrorProneOptions(ErrorProneOptions errorProneOptions) {

        errorProneOptions.disable(
                "AutoCloseableMustBeClosed",
                "CatchSpecificity",
                "CanIgnoreReturnValueSuggester",
                // https://github.com/google/error-prone/issues/4544
                "DistinctVarargsChecker",
                "InlineMeSuggester",
                // LambdaMethodReference is incredibly expensive, see #2997. We leave it
                // here to employ as a cleanup, but don't execute it in most compilations.
                "LambdaMethodReference",
                // We often use javadoc comments without javadoc parameter information.
                "NotJavadoc",
                "PreferImmutableStreamExCollections",
                "UnnecessaryTestMethodPrefix",
                "UnusedVariable",
                // See VarUsage: The var keyword results in illegible code in most cases and should not be used.
                "Varifier",
                // Yoda style should not block baseline upgrades.
                "YodaCondition",

                // Disable new error-prone checks added in 2.24.0
                // See https://github.com/google/error-prone/releases/tag/v2.24.0
                "MultipleNullnessAnnotations",
                "NullableTypeParameter",
                "NullableWildcard",
                // This check is a generalization of the old 'SuperEqualsIsObjectEquals', so by disabling
                // it we lose a bit of protection for the time being, but it's a small price to pay for
                // seamless rollout.
                "SuperCallToObjectMethod");

        errorProneOptions.error(
                "EqualsHashCode",
                "EqualsIncompatibleType",
                "StreamResourceLeak",
                "InputStreamSlowMultibyteRead",
                "JavaDurationGetSecondsGetNano",
                "URLEqualsHashCode",
                "BoxedPrimitiveEquality",
                "ReferenceEquality");
        // Relax some checks for test code
        if (errorProneOptions.getCompilingTestOnlyCode().get()) {
            errorProneOptions.disable("UnnecessaryLambda");
        }
    }
}
