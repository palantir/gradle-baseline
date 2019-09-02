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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.BugCheckerInfo;
import com.google.errorprone.scanner.BuiltInCheckerSuppliers;
import com.palantir.baseline.errorprone.CatchBlockLogException;
import com.palantir.baseline.errorprone.DangerousCompletableFutureUsage;
import com.palantir.baseline.errorprone.DangerousJsonTypeInfoUsage;
import com.palantir.baseline.errorprone.DangerousParallelStreamUsage;
import com.palantir.baseline.errorprone.DangerousStringInternUsage;
import com.palantir.baseline.errorprone.DangerousThreadPoolExecutorUsage;
import com.palantir.baseline.errorprone.DangerousThrowableMessageSafeArg;
import com.palantir.baseline.errorprone.GradleCacheableTaskAction;
import com.palantir.baseline.errorprone.GuavaPreconditionsMessageFormat;
import com.palantir.baseline.errorprone.JUnit5RuleUsage;
import com.palantir.baseline.errorprone.LambdaMethodReference;
import com.palantir.baseline.errorprone.LogSafePreconditionsMessageFormat;
import com.palantir.baseline.errorprone.NonComparableStreamSort;
import com.palantir.baseline.errorprone.OptionalOrElseMethodInvocation;
import com.palantir.baseline.errorprone.OptionalOrElseThrowThrows;
import com.palantir.baseline.errorprone.PreconditionsConstantMessage;
import com.palantir.baseline.errorprone.PreferBuiltInConcurrentKeySet;
import com.palantir.baseline.errorprone.PreferCollectionTransform;
import com.palantir.baseline.errorprone.PreferListsPartition;
import com.palantir.baseline.errorprone.PreferSafeLoggableExceptions;
import com.palantir.baseline.errorprone.PreferSafeLoggingPreconditions;
import com.palantir.baseline.errorprone.PreventTokenLogging;
import com.palantir.baseline.errorprone.ShutdownHook;
import com.palantir.baseline.errorprone.Slf4jConstantLogMessage;
import com.palantir.baseline.errorprone.Slf4jLogsafeArgs;
import com.palantir.baseline.errorprone.SwitchStatementDefaultCase;
import com.palantir.baseline.errorprone.ValidateConstantMessage;
import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.baseline.tasks.RefasterCompileTask;
import java.io.File;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

public final class BaselineErrorProne implements Plugin<Project> {

    public static final String REFASTER_CONFIGURATION = "refaster";
    public static final String EXTENSION_NAME = "baselineErrorProne";
    public static final String PROP_STRICT = "com.palantir.baseline-error-prone.strict";

    private static final String ERROR_PRONE_JAVAC_VERSION = "9+181-r4173-1";
    private static final String PROP_ERROR_PRONE_APPLY = "errorProneApply";
    private static final String PROP_REFASTER_APPLY = "refasterApply";

    private static final ImmutableSet<BugCheckerInfo> allBaselineChecks =
            BuiltInCheckerSuppliers.getSuppliers(ImmutableList.of(
            CatchBlockLogException.class,
            DangerousCompletableFutureUsage.class,
            DangerousJsonTypeInfoUsage.class,
            DangerousParallelStreamUsage.class,
            DangerousStringInternUsage.class,
            DangerousThreadPoolExecutorUsage.class,
            DangerousThrowableMessageSafeArg.class,
            GradleCacheableTaskAction.class,
            GuavaPreconditionsMessageFormat.class,
            JUnit5RuleUsage.class,
            LambdaMethodReference.class,
            LogSafePreconditionsMessageFormat.class,
            NonComparableStreamSort.class,
            OptionalOrElseMethodInvocation.class,
            OptionalOrElseThrowThrows.class,
            PreconditionsConstantMessage.class,
            PreferBuiltInConcurrentKeySet.class,
            PreferCollectionTransform.class,
            PreferListsPartition.class,
            PreferSafeLoggableExceptions.class,
            PreferSafeLoggingPreconditions.class,
            PreventTokenLogging.class,
            ShutdownHook.class,
            Slf4jConstantLogMessage.class,
            Slf4jLogsafeArgs.class,
            SwitchStatementDefaultCase.class,
            ValidateConstantMessage.class
    ));

    private static final ImmutableSet<String> excludedChecks = ImmutableSet.of(
            "AndroidJdkLibsChecker", // ignore Android
            "Java7ApiChecker", // we require JDK8+
            "StaticOrDefaultInterfaceMethod", // Android specific
            "Var" // high noise, low signal
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            BaselineErrorProneExtension errorProneExtension = project.getExtensions()
                    .create(EXTENSION_NAME, BaselineErrorProneExtension.class, project);
            project.getPluginManager().apply(ErrorPronePlugin.class);

            String version = Optional.ofNullable(getClass().getPackage().getImplementationVersion())
                    .orElse("latest.release");

            Configuration refasterConfiguration = project.getConfigurations().create(REFASTER_CONFIGURATION);
            Configuration refasterCompilerConfiguration = project.getConfigurations()
                    .create("refasterCompiler", configuration -> configuration.extendsFrom(refasterConfiguration));

            project.getDependencies().add(
                    REFASTER_CONFIGURATION,
                    "com.palantir.baseline:baseline-refaster-rules:" + version + ":sources");
            project.getDependencies().add(
                    ErrorPronePlugin.CONFIGURATION_NAME,
                    "com.palantir.baseline:baseline-error-prone:" + version);
            project.getDependencies().add(
                    "refasterCompiler",
                    "com.palantir.baseline:baseline-refaster-javac-plugin:" + version);

            Provider<File> refasterRulesFile = project.getLayout().getBuildDirectory()
                    .file("refaster/rules.refaster")
                    .map(RegularFile::getAsFile);

            Task compileRefaster = project.getTasks().create("compileRefaster", RefasterCompileTask.class, task -> {
                task.setSource(refasterConfiguration);
                task.getRefasterSources().set(refasterConfiguration);
                task.setClasspath(refasterCompilerConfiguration);
                task.getRefasterRulesFile().set(refasterRulesFile);
            });

            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                JavaVersion jdkVersion = JavaVersion.toVersion(javaCompile.getToolChain().getVersion());

                ((ExtensionAware) javaCompile.getOptions()).getExtensions()
                        .configure(
                                ErrorProneOptions.class,
                                configureErrorProne(
                                        project,
                                        javaCompile,
                                        jdkVersion,
                                        errorProneExtension,
                                        refasterRulesFile,
                                        compileRefaster
                                ));
            });

            // To allow refactoring of deprecated methods, even when -Xlint:deprecation is specified, we need to remove
            // these compiler flags after all configuration has happened.
            project.afterEvaluate(unused -> project.getTasks().withType(JavaCompile.class)
                    .configureEach(javaCompile -> {
                        if (javaCompile.equals(compileRefaster)) {
                            return;
                        }
                        if (isRefactoring(project)) {
                            javaCompile.getOptions().setWarnings(false);
                            javaCompile.getOptions().setDeprecation(false);
                            javaCompile.getOptions().setCompilerArgs(javaCompile.getOptions().getCompilerArgs()
                                    .stream()
                                    .filter(arg -> !arg.equals("-Werror"))
                                    .filter(arg -> !arg.equals("-deprecation"))
                                    .filter(arg -> !arg.equals("-Xlint:deprecation"))
                                    .collect(Collectors.toList()));
                        }
                    }));

            project.getPluginManager().withPlugin("java-gradle-plugin", appliedPlugin -> {
                project.getTasks().withType(JavaCompile.class).configureEach(javaCompile ->
                        ((ExtensionAware) javaCompile.getOptions()).getExtensions()
                                .configure(ErrorProneOptions.class, errorProneOptions -> {
                                    errorProneOptions.check("Slf4jLogsafeArgs", CheckSeverity.OFF);
                                    errorProneOptions.check("PreferSafeLoggableExceptions", CheckSeverity.OFF);
                                    errorProneOptions.check("PreferSafeLoggingPreconditions", CheckSeverity.OFF);
                                    errorProneOptions.check("PreconditionsConstantMessage", CheckSeverity.OFF);
                                }));
            });

            // In case of java 8 we need to add errorprone javac compiler to bootstrap classpath of tasks that perform
            // compilation or code analysis. ErrorProneJavacPluginPlugin handles JavaCompile cases via errorproneJavac
            // configuration and we do similar thing for Test and Javadoc type tasks
            if (!JavaVersion.current().isJava9Compatible()) {
                project.getDependencies().add(
                        ErrorPronePlugin.JAVAC_CONFIGURATION_NAME,
                        "com.google.errorprone:javac:" + ERROR_PRONE_JAVAC_VERSION);
                project.getConfigurations()
                        .named(ErrorPronePlugin.JAVAC_CONFIGURATION_NAME)
                        .configure(conf -> {
                            List<File> bootstrapClasspath = Splitter.on(File.pathSeparator)
                                    .splitToList(System.getProperty("sun.boot.class.path"))
                                    .stream()
                                    .map(File::new)
                                    .collect(Collectors.toList());
                            FileCollection errorProneFiles = conf.plus(project.files(bootstrapClasspath));
                            project.getTasks().withType(Test.class)
                                    .configureEach(test -> test.setBootstrapClasspath(errorProneFiles));
                            project.getTasks().withType(Javadoc.class)
                                    .configureEach(javadoc -> javadoc.getOptions()
                                            .setBootClasspath(new LazyConfigurationList(errorProneFiles)));
                        });
            }
        });
    }

    private Action<ErrorProneOptions> configureErrorProne(
            Project project,
            JavaCompile javaCompile,
            JavaVersion jdkVersion,
            BaselineErrorProneExtension errorProneExtension,
            Provider<File> refasterRulesFile,
            Task compileRefaster) {
        return errorProneOptions -> {
            errorProneOptions.setEnabled(true);
            errorProneOptions.setDisableWarningsInGeneratedCode(true);
            errorProneOptions.check("EqualsHashCode", CheckSeverity.ERROR);
            errorProneOptions.check("EqualsIncompatibleType", CheckSeverity.ERROR);
            errorProneOptions.check("StreamResourceLeak", CheckSeverity.ERROR);
            errorProneOptions.check("InputStreamSlowMultibyteRead", CheckSeverity.ERROR);
            errorProneOptions.check("JavaDurationGetSecondsGetNano", CheckSeverity.ERROR);
            errorProneOptions.check("URLEqualsHashCode", CheckSeverity.ERROR);

            if (isStrict(project) && !isRefactoring(project)) {
                Set<String> compilerArgs = ImmutableSet.of("-Werror", "-Xlint:deprecation", "-Xlint:unchecked");
                javaCompile.getOptions().getCompilerArgs().addAll(compilerArgs);
                errorProneOptions.getErrorproneArgs().addAll(compilerArgs);
                Set<String> checksToPromote = strictModeChecksToPromote();
                for (String checkName : checksToPromote) {
                    errorProneOptions.check(checkName, CheckSeverity.ERROR);
                }
                project.getLogger().info("Enabling strict error-prone checks and promoting warnings to errors, "
                        + "compile args: {}, checks: {}", compilerArgs, checksToPromote);
            }

            if (jdkVersion.compareTo(JavaVersion.toVersion("12.0.1")) >= 0) {
                // Errorprone isn't officially compatible with Java12, but in practise everything
                // works apart from this one check: https://github.com/google/error-prone/issues/1106
                errorProneOptions.check("Finally", CheckSeverity.OFF);
            }

            if (jdkVersion.compareTo(JavaVersion.toVersion("13.0.0")) >= 0) {
                // Errorprone isn't officially compatible with Java13 either
                // https://github.com/google/error-prone/issues/1106
                errorProneOptions.check("TypeParameterUnusedInFormals", CheckSeverity.OFF);
            }

            if (javaCompile.equals(compileRefaster)) {
                // Don't apply refaster to itself...
                return;
            }

            if (isRefactoring(project)) {
                // Don't attempt to cache since it won't capture the source files that might be modified
                javaCompile.getOutputs().cacheIf(t -> false);

                if (isRefasterRefactoring(project)) {
                    javaCompile.dependsOn(compileRefaster);
                    errorProneOptions.getErrorproneArgumentProviders().add(() -> ImmutableList.of(
                            "-XepPatchChecks:refaster:" + refasterRulesFile.get().getAbsolutePath(),
                            "-XepPatchLocation:IN_PLACE"));
                }

                if (isErrorProneRefactoring(project)) {
                    // TODO(gatesn): Is there a way to discover error-prone checks?
                    // Maybe service-load from a ClassLoader configured with annotation processor path?
                    // https://github.com/google/error-prone/pull/947
                    errorProneOptions.getErrorproneArgumentProviders().add(() -> ImmutableList.of(
                            "-XepPatchChecks:" + Joiner.on(',')
                                    .join(errorProneExtension.getPatchChecks().get()),
                            "-XepPatchLocation:IN_PLACE"));
                }
            }
        };
    }

    private boolean isRefactoring(Project project) {
        return isRefasterRefactoring(project) || isErrorProneRefactoring(project);
    }

    private boolean isRefasterRefactoring(Project project) {
        return project.hasProperty(PROP_REFASTER_APPLY);
    }

    private boolean isErrorProneRefactoring(Project project) {
        return project.hasProperty(PROP_ERROR_PRONE_APPLY);
    }

    private boolean isStrict(Project project) {
        return project.hasProperty(PROP_STRICT) && Boolean.TRUE.equals(project.property(PROP_STRICT));
    }

    private static Set<String> strictModeChecksToPromote() {
        return Stream.of(
                allBaselineChecks,
                BuiltInCheckerSuppliers.ENABLED_WARNINGS,
                BuiltInCheckerSuppliers.DISABLED_CHECKS)
                .flatMap(Collection::stream)
                .map(BugCheckerInfo::canonicalName)
                .filter(check -> !excludedChecks.contains(check))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    }

    private static final class LazyConfigurationList extends AbstractList<File> {
        private final FileCollection files;
        private List<File> fileList;

        private LazyConfigurationList(FileCollection files) {
            this.files = files;
        }

        @Override
        public File get(int index) {
            if (fileList == null) {
                fileList = ImmutableList.copyOf(files.getFiles());
            }
            return fileList.get(index);
        }

        @Override
        public int size() {
            if (fileList == null) {
                fileList = ImmutableList.copyOf(files.getFiles());
            }
            return fileList.size();
        }
    }

}
