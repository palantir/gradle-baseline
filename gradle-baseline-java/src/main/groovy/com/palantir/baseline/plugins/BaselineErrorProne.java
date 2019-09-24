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
import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.baseline.tasks.RefasterCompileTask;
import java.io.File;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

public final class BaselineErrorProne implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineErrorProne.class);
    public static final String EXTENSION_NAME = "baselineErrorProne";
    private static final String ERROR_PRONE_JAVAC_VERSION = "9+181-r4173-1";
    private static final String PROP_ERROR_PRONE_APPLY = "errorProneApply";
    private static final String PROP_REFASTER_APPLY = "refasterApply";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        BaselineErrorProneExtension errorProneExtension = project.getExtensions()
                .create(EXTENSION_NAME, BaselineErrorProneExtension.class, project);
        project.getPluginManager().apply(ErrorPronePlugin.class);

        String version = Optional.ofNullable(BaselineErrorProne.class.getPackage().getImplementationVersion())
                .orElseGet(() -> {
                    log.warn("Baseline is using 'latest.release' - beware this compromises build reproducibility");
                    return "latest.release";
                });

        Configuration refasterConfiguration = project.getConfigurations().create("refaster", conf -> {
            conf.defaultDependencies(deps -> {
                deps.add(project.getDependencies().create(
                        "com.palantir.baseline:baseline-refaster-rules:" + version + ":sources"));
            });
        });
        Configuration refasterCompilerConfiguration = project.getConfigurations()
                .create("refasterCompiler", configuration -> configuration.extendsFrom(refasterConfiguration));

        project.getDependencies().add(
                ErrorPronePlugin.CONFIGURATION_NAME,
                "com.palantir.baseline:baseline-error-prone:" + version);
        project.getDependencies().add(
                "refasterCompiler",
                "com.palantir.baseline:baseline-refaster-javac-plugin:" + version);

        Provider<File> refasterRulesFile = project.getLayout().getBuildDirectory()
                .file("refaster/rules.refaster")
                .map(RegularFile::getAsFile);

        RefasterCompileTask compileRefaster =
                project.getTasks().create("compileRefaster", RefasterCompileTask.class, task -> {
                    task.setSource(refasterConfiguration);
                    task.getRefasterSources().set(refasterConfiguration);
                    task.setClasspath(refasterCompilerConfiguration);
                    task.getRefasterRulesFile().set(refasterRulesFile);
                });

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            ((ExtensionAware) javaCompile.getOptions()).getExtensions()
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
            project.getDependencies().add(ErrorPronePlugin.JAVAC_CONFIGURATION_NAME,
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
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void configureErrorProneOptions(
            Project project,
            Provider<File> refasterRulesFile,
            RefasterCompileTask compileRefaster,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {
        JavaVersion jdkVersion = JavaVersion.toVersion(javaCompile.getToolChain().getVersion());

        errorProneOptions.setEnabled(true);
        errorProneOptions.setDisableWarningsInGeneratedCode(true);
        errorProneOptions.setExcludedPaths(
                String.format("%s/(build|src/generated.*)/.*", project.getProjectDir().getPath()));
        errorProneOptions.check("UnusedVariable", CheckSeverity.OFF);
        errorProneOptions.check("EqualsHashCode", CheckSeverity.ERROR);
        errorProneOptions.check("EqualsIncompatibleType", CheckSeverity.ERROR);
        errorProneOptions.check("StreamResourceLeak", CheckSeverity.ERROR);
        errorProneOptions.check("InputStreamSlowMultibyteRead", CheckSeverity.ERROR);
        errorProneOptions.check("JavaDurationGetSecondsGetNano", CheckSeverity.ERROR);
        errorProneOptions.check("URLEqualsHashCode", CheckSeverity.ERROR);

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
                errorProneOptions.getErrorproneArgumentProviders().add(() -> {
                    String file = refasterRulesFile.get().getAbsolutePath();
                    return new File(file).exists()
                            ? ImmutableList.of(
                            "-XepPatchChecks:refaster:" + file,
                            "-XepPatchLocation:IN_PLACE")
                            : Collections.emptyList();
                });
            }

            if (isErrorProneRefactoring(project)) {
                // TODO(gatesn): Is there a way to discover error-prone checks?
                // Maybe service-load from a ClassLoader configured with annotation processor path?
                // https://github.com/google/error-prone/pull/947
                errorProneOptions.getErrorproneArgumentProviders().add(() -> {
                    // Don't apply checks that have been explicitly disabled
                    Stream<String> errorProneChecks = getNotDisabledErrorproneChecks(
                            errorProneExtension, javaCompile, errorProneOptions);
                    return ImmutableList.of(
                            "-XepPatchChecks:" + Joiner.on(',').join(errorProneChecks.iterator()),
                            "-XepPatchLocation:IN_PLACE");
                });
            }
        }
    }

    private static Stream<String> getNotDisabledErrorproneChecks(
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {
        return errorProneExtension.getPatchChecks().get().stream().filter(check -> {
            if (checkExplicitlyDisabled(errorProneOptions, check)) {
                log.info(
                        "Task {}: not applying errorprone check {} because it has severity OFF in errorProneOptions",
                        javaCompile.getPath(),
                        check);
                return false;
            }
            return true;
        });
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

    private static boolean checkExplicitlyDisabled(ErrorProneOptions errorProneOptions, String check) {
        Map<String, CheckSeverity> checks = errorProneOptions.getChecks();
        return checks.get(check) == CheckSeverity.OFF
                || errorProneOptions.getErrorproneArgs().contains(String.format("-Xep:%s:OFF", check));
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
