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

import com.google.common.collect.Iterables;
import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorPronePlugin;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;

public final class BaselineErrorProne implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineErrorProne.class);
    public static final String EXTENSION_NAME = "baselineErrorProne";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        BaselineErrorProneExtension errorProneExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineErrorProneExtension.class);
        project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);

        String version = Optional.ofNullable((String) project.findProperty("baselineErrorProneVersion"))
                .or(() -> Optional.ofNullable(
                        BaselineErrorProne.class.getPackage().getImplementationVersion()))
                .orElseThrow(() -> new RuntimeException("BaselineErrorProne implementation version not found"));

        project.getDependencies()
                .add(ErrorPronePlugin.CONFIGURATION_NAME, "com.palantir.baseline:baseline-error-prone:" + version);

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        configureErrorProneOptions(project, errorProneExtension, javaCompile, errorProneOptions);
                    });
        });

        project.getPluginManager().withPlugin("java-gradle-plugin", appliedPlugin -> {
            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> ((ExtensionAware)
                            javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        errorProneOptions.disable("CatchBlockLogException");
                        errorProneOptions.disable("JavaxInjectOnAbstractMethod");
                        errorProneOptions.disable("PreconditionsConstantMessage");
                        errorProneOptions.disable("PreferSafeLoggableExceptions");
                        errorProneOptions.disable("PreferSafeLogger");
                        errorProneOptions.disable("PreferSafeLoggingPreconditions");
                        errorProneOptions.disable("Slf4jConstantLogMessage");
                        errorProneOptions.disable("Slf4jLogsafeArgs");
                    }));
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void configureErrorProneOptions(
            Project project,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {

        errorProneOptions.disable(
                "AutoCloseableMustBeClosed",
                "CatchSpecificity",
                "CanIgnoreReturnValueSuggester",
                "InlineMeSuggester",
                // We often use javadoc comments without javadoc parameter information.
                "NotJavadoc",
                "PreferImmutableStreamExCollections",
                // StringCaseLocaleUsage duplicates our existing DefaultLocale check which is already
                // enforced in some places.
                "StringCaseLocaleUsage",
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

        // This makes no sense, we're adding the patch check globally based on if it's in any source set
        addChecksIfModuleExistsInSourceSet(
                project,
                errorProneExtension,
                javaCompile,
                errorProneOptions,
                "com.palantir.safe-logging",
                "preconditions",
                "PreferSafeLoggingPreconditions",
                "PreferSafeLoggableExceptions");

        addChecksIfModuleExistsInSourceSet(
                project,
                errorProneExtension,
                javaCompile,
                errorProneOptions,
                "com.palantir.safe-logging",
                "logger",
                "PreferSafeLogger");
    }

    private static void addChecksIfModuleExistsInSourceSet(
            Project project,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions,
            String group,
            String module,
            String... checks) {
        errorProneExtension.getPatchChecks().addAll(project.provider(() -> {
            boolean hasModule = project
                    .getExtensions()
                    .getByType(SourceSetContainer.class)
                    .matching(sourceSet -> sourceSet.getCompileJavaTaskName().equals(javaCompile.getName()))
                    .stream()
                    .findFirst()
                    .filter(sourceSet -> {
                        Configuration compileClasspath =
                                project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());

                        return hasDependenciesMatching(
                                compileClasspath,
                                mci -> Objects.equals(mci.getGroup(), group)
                                        && Objects.equals(mci.getModule(), module));
                    })
                    .isPresent();

            if (!hasModule) {
                return List.of();
            }

            return Stream.of(checks)
                    .filter(check -> {
                        if (checkExplicitlyDisabled(errorProneOptions, check)) {
                            log.info(
                                    "Task {}: not applying errorprone check {} because "
                                            + "it has severity OFF in errorProneOptions",
                                    javaCompile.getPath(),
                                    check);
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }));
    }

    private static boolean hasDependenciesMatching(Configuration configuration, Spec<ModuleComponentIdentifier> spec) {
        return !Iterables.isEmpty(configuration
                .getIncoming()
                .artifactView(viewConfiguration -> viewConfiguration.componentFilter(ci ->
                        ci instanceof ModuleComponentIdentifier && spec.isSatisfiedBy((ModuleComponentIdentifier) ci)))
                .getArtifacts());
    }

    private static boolean checkExplicitlyDisabled(ErrorProneOptions errorProneOptions, String check) {
        Map<String, CheckSeverity> checks = errorProneOptions.getChecks().get();
        return checks.get(check) == CheckSeverity.OFF
                || errorProneOptions.getErrorproneArgs().get().contains(String.format("-Xep:%s:OFF", check));
    }
}
