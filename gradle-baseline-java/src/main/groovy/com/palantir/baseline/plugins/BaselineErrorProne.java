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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.palantir.baseline.IntellijSupport;
import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.baseline.tasks.CompileRefasterTask;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineErrorProne implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineErrorProne.class);
    public static final String EXTENSION_NAME = "baselineErrorProne";
    private static final String PROP_ERROR_PRONE_APPLY = "errorProneApply";
    private static final String PROP_REFASTER_APPLY = "refasterApply";
    private static final String DISABLE_PROPERTY = "com.palantir.baseline-error-prone.disable";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        BaselineErrorProneExtension errorProneExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineErrorProneExtension.class, project);
        project.getPluginManager().apply(ErrorPronePlugin.class);

        String version = Optional.ofNullable(
                        BaselineErrorProne.class.getPackage().getImplementationVersion())
                .orElseGet(() -> {
                    log.warn("Baseline is using 'latest.release' - beware this compromises build reproducibility");
                    return "latest.release";
                });
        Configuration refasterConfiguration = project.getConfigurations().create("refaster", conf -> {
            conf.defaultDependencies(deps -> {
                deps.add(project.getDependencies()
                        .create("com.palantir.baseline:baseline-refaster-rules:" + version + ":sources"));
            });
        });
        Configuration refasterCompilerConfiguration = project.getConfigurations()
                .create("refasterCompiler", configuration -> configuration.extendsFrom(refasterConfiguration));

        project.getDependencies()
                .add(ErrorPronePlugin.CONFIGURATION_NAME, "com.palantir.baseline:baseline-error-prone:" + version);
        project.getDependencies()
                .add("refasterCompiler", "com.palantir.baseline:baseline-refaster-javac-plugin:" + version);

        Provider<File> refasterRulesFile = project.getLayout()
                .getBuildDirectory()
                .file("refaster/rules.refaster")
                .map(RegularFile::getAsFile);

        TaskProvider<CompileRefasterTask> compileRefaster = project.getTasks()
                .register("compileRefaster", CompileRefasterTask.class, task -> {
                    task.setSource(refasterConfiguration);
                    task.getRefasterSources().set(refasterConfiguration);
                    task.setClasspath(refasterCompilerConfiguration);
                    task.getRefasterRulesFile().set(refasterRulesFile);
                });

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        configureErrorProneOptions(
                                project,
                                refasterRulesFile,
                                compileRefaster,
                                errorProneExtension,
                                javaCompile,
                                errorProneOptions);
                    });
        });

        // To allow refactoring of deprecated methods, even when -Xlint:deprecation is specified, we need to remove
        // these compiler flags after all configuration has happened.
        project.afterEvaluate(
                unused -> project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                    if (javaCompile.getName().equals(compileRefaster.getName())) {
                        return;
                    }
                    if (isRefactoring(project)) {
                        javaCompile.getOptions().setWarnings(false);
                        javaCompile.getOptions().setDeprecation(false);
                        javaCompile
                                .getOptions()
                                .setCompilerArgs(javaCompile.getOptions().getCompilerArgs().stream()
                                        .filter(arg -> !arg.equals("-Werror"))
                                        .filter(arg -> !arg.equals("-deprecation"))
                                        .filter(arg -> !arg.equals("-Xlint:deprecation"))
                                        .collect(Collectors.toList()));
                    }
                }));

        project.getPluginManager().withPlugin("java-gradle-plugin", appliedPlugin -> {
            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> ((ExtensionAware)
                            javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        errorProneOptions.check("Slf4jLogsafeArgs", CheckSeverity.OFF);
                        errorProneOptions.check("PreferSafeLoggableExceptions", CheckSeverity.OFF);
                        errorProneOptions.check("PreferSafeLogger", CheckSeverity.OFF);
                        errorProneOptions.check("PreferSafeLoggingPreconditions", CheckSeverity.OFF);
                        errorProneOptions.check("PreconditionsConstantMessage", CheckSeverity.OFF);
                    }));
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void configureErrorProneOptions(
            Project project,
            Provider<File> refasterRulesFile,
            TaskProvider<CompileRefasterTask> compileRefaster,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {
        if (isDisabled(project)) {
            errorProneOptions.getEnabled().set(false);
        }

        errorProneOptions.getDisableWarningsInGeneratedCode().set(true);
        errorProneOptions.getExcludedPaths().set(excludedPathsRegex());

        errorProneOptions.disable(
                "AutoCloseableMustBeClosed",
                "CatchSpecificity",
                "CanIgnoreReturnValueSuggester",
                "InlineMeSuggester",
                "PreferImmutableStreamExCollections",
                "UnusedVariable",
                // See VarUsage: The var keyword results in illegible code in most cases and should not be used.
                "Varifier");
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

        if (javaCompile.getName().equals(compileRefaster.getName())) {
            // Don't apply refaster to itself...
            return;
        }

        if (isRefactoring(project)) {
            // Don't attempt to cache since it won't capture the source files that might be modified
            javaCompile.getOutputs().cacheIf(t -> false);

            if (isRefasterRefactoring(project)) {
                javaCompile.dependsOn(compileRefaster);
                errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                    // intentionally not using a lambda to reduce gradle warnings
                    @Override
                    public Iterable<String> asArguments() {
                        String file = refasterRulesFile.get().getAbsolutePath();
                        return new File(file).exists()
                                ? ImmutableList.of("-XepPatchChecks:refaster:" + file, "-XepPatchLocation:IN_PLACE")
                                : Collections.emptyList();
                    }
                });
            }

            if (isErrorProneRefactoring(project)) {
                Optional<SourceSet> maybeSourceSet = project
                        .getConvention()
                        .getPlugin(JavaPluginConvention.class)
                        .getSourceSets()
                        .matching(ss -> javaCompile.getName().equals(ss.getCompileJavaTaskName()))
                        .stream()
                        .collect(MoreCollectors.toOptional());

                // TODO(gatesn): Is there a way to discover error-prone checks?
                // Maybe service-load from a ClassLoader configured with annotation processor path?
                // https://github.com/google/error-prone/pull/947
                errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                    // intentionally not using a lambda to reduce gradle warnings
                    @Override
                    public Iterable<String> asArguments() {
                        // Don't apply checks that have been explicitly disabled
                        Stream<String> errorProneChecks = getSpecificErrorProneChecks(project)
                                .orElseGet(() -> getNotDisabledErrorproneChecks(
                                        project, errorProneExtension, javaCompile, maybeSourceSet, errorProneOptions));
                        return ImmutableList.of(
                                "-XepPatchChecks:" + Joiner.on(',').join(errorProneChecks.iterator()),
                                "-XepPatchLocation:IN_PLACE");
                    }
                });
            }
        }
    }

    static String excludedPathsRegex() {
        // Error-prone normalizes filenames to use '/' path separator:
        // https://github.com/google/error-prone/blob/c601758e81723a8efc4671726b8363be7a306dce
        // /check_api/src/main/java/com/google/errorprone/util/ASTHelpers.java#L1277-L1285
        return ".*/(build|generated_.*[sS]rc|src/generated.*)/.*";
    }

    private static Optional<Stream<String>> getSpecificErrorProneChecks(Project project) {
        return Optional.ofNullable(project.findProperty(PROP_ERROR_PRONE_APPLY))
                .map(Objects::toString)
                .flatMap(value -> Optional.ofNullable(Strings.emptyToNull(value)))
                .map(value -> Splitter.on(',').trimResults().omitEmptyStrings().splitToList(value))
                .flatMap(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.stream()));
    }

    private static Stream<String> getNotDisabledErrorproneChecks(
            Project project,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            Optional<SourceSet> maybeSourceSet,
            ErrorProneOptions errorProneOptions) {
        // If this javaCompile is associated with a source set, use it to figure out if it has preconditions or not.
        Predicate<String> filterOutPreconditions = maybeSourceSet
                .map(ss -> {
                    Configuration configuration =
                            project.getConfigurations().findByName(ss.getCompileClasspathConfigurationName());
                    if (configuration == null) {
                        return null;
                    }
                    return filterOutPreconditions(configuration).and(filterOutSafeLogger(configuration));
                })
                .orElse(check -> true);

        return errorProneExtension.getPatchChecks().get().stream().filter(check -> {
            if (checkExplicitlyDisabled(errorProneOptions, check)) {
                log.info(
                        "Task {}: not applying errorprone check {} because it has severity OFF in errorProneOptions",
                        javaCompile.getPath(),
                        check);
                return false;
            }
            return filterOutPreconditions.test(check);
        });
    }

    private static boolean hasDependenciesMatching(Configuration configuration, Spec<ModuleComponentIdentifier> spec) {
        return !Iterables.isEmpty(configuration
                .getIncoming()
                .artifactView(viewConfiguration -> viewConfiguration.componentFilter(ci ->
                        ci instanceof ModuleComponentIdentifier && spec.isSatisfiedBy((ModuleComponentIdentifier) ci)))
                .getArtifacts());
    }

    /** Filters out preconditions checks if the required libraries are not on the classpath. */
    public static Predicate<String> filterOutPreconditions(Configuration compileClasspath) {
        return filterOutBasedOnDependency(
                compileClasspath,
                "com.palantir.safe-logging",
                "preconditions",
                "PreferSafeLoggingPreconditions",
                "PreferSafeLoggableExceptions");
    }

    /** Filters out PreferSafeLogger if the required libraries are not on the classpath. */
    private static Predicate<String> filterOutSafeLogger(Configuration compileClasspath) {
        return filterOutBasedOnDependency(compileClasspath, "com.palantir.safe-logging", "logger", "PreferSafeLogger");
    }

    private static Predicate<String> filterOutBasedOnDependency(
            Configuration compileClasspath, String dependencyGroup, String dependencyModule, String... checkNames) {
        boolean hasDependency = hasDependenciesMatching(
                compileClasspath,
                mci -> Objects.equals(mci.getGroup(), dependencyGroup)
                        && Objects.equals(mci.getModule(), dependencyModule));
        return check -> {
            if (!hasDependency) {
                for (String checkName : checkNames) {
                    if (Objects.equals(checkName, check)) {
                        log.info(
                                "Disabling check {} as '{}:{}' missing from {}",
                                checkName,
                                dependencyGroup,
                                dependencyModule,
                                compileClasspath);
                        return false;
                    }
                }
            }
            return true;
        };
    }

    private static boolean isRefactoring(Project project) {
        return isRefasterRefactoring(project) || isErrorProneRefactoring(project);
    }

    private static boolean isRefasterRefactoring(Project project) {
        return project.hasProperty(PROP_REFASTER_APPLY);
    }

    private static boolean isErrorProneRefactoring(Project project) {
        return project.hasProperty(PROP_ERROR_PRONE_APPLY);
    }

    private static boolean isDisabled(Project project) {
        Object disable = project.findProperty(DISABLE_PROPERTY);
        if (disable == null) {
            return IntellijSupport.isRunningInIntellij();
        } else {
            return !disable.equals("false");
        }
    }

    private static boolean checkExplicitlyDisabled(ErrorProneOptions errorProneOptions, String check) {
        Map<String, CheckSeverity> checks = errorProneOptions.getChecks().get();
        return checks.get(check) == CheckSeverity.OFF
                || errorProneOptions.getErrorproneArgs().get().contains(String.format("-Xep:%s:OFF", check));
    }
}
