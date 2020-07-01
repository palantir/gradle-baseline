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
import com.palantir.baseline.extensions.BaselineErrorProneExtension;
import com.palantir.baseline.tasks.CompileRefasterTask;
import java.io.File;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineErrorProne implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineErrorProne.class);
    public static final String EXTENSION_NAME = "baselineErrorProne";
    private static final String ERROR_PRONE_JAVAC_VERSION = "9+181-r4173-1";
    private static final String PROP_ERROR_PRONE_APPLY = "errorProneApply";
    private static final String PROP_REFASTER_APPLY = "refasterApply";
    private static final String DISABLE_PROPERY = "com.palantir.baseline-error-prone.disable";

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

        CompileRefasterTask compileRefaster = project.getTasks()
                .create("compileRefaster", CompileRefasterTask.class, task -> {
                    task.setSource(refasterConfiguration);
                    task.getRefasterSources().set(refasterConfiguration);
                    task.setClasspath(refasterCompilerConfiguration);
                    task.getRefasterRulesFile().set(refasterRulesFile);
                });

        // In case of java 8 we need to add errorprone javac compiler to bootstrap classpath of tasks that perform
        // compilation or code analysis. ErrorProneJavacPluginPlugin handles JavaCompile cases via errorproneJavac
        // configuration and we do similar thing for Test and Javadoc type tasks
        if (!JavaVersion.current().isJava9Compatible()) {
            project.getDependencies()
                    .add(
                            ErrorPronePlugin.JAVAC_CONFIGURATION_NAME,
                            "com.google.errorprone:javac:" + ERROR_PRONE_JAVAC_VERSION);
            project.getConfigurations()
                    .named(ErrorPronePlugin.JAVAC_CONFIGURATION_NAME)
                    .configure(
                            conf -> {
                                List<File> bootstrapClasspath = Splitter.on(File.pathSeparator)
                                        .splitToList(System.getProperty("sun.boot.class.path"))
                                        .stream()
                                        .map(File::new)
                                        .collect(Collectors.toList());
                                FileCollection errorProneFiles = conf.plus(project.files(bootstrapClasspath));
                                project.getTasks()
                                        .withType(Test.class)
                                        .configureEach(test -> test.setBootstrapClasspath(errorProneFiles));
                                project.getTasks().withType(Javadoc.class).configureEach(javadoc -> javadoc.getOptions()
                                        .setBootClasspath(new LazyConfigurationList(errorProneFiles)));
                            });
        }

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
                    if (javaCompile.equals(compileRefaster)) {
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
                        errorProneOptions.check("PreferSafeLoggingPreconditions", CheckSeverity.OFF);
                        errorProneOptions.check("PreconditionsConstantMessage", CheckSeverity.OFF);
                    }));
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void configureErrorProneOptions(
            Project project,
            Provider<File> refasterRulesFile,
            CompileRefasterTask compileRefaster,
            BaselineErrorProneExtension errorProneExtension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {

        if (project.hasProperty(DISABLE_PROPERY)) {
            log.info("Disabling baseline-error-prone for {} due to {}", project, DISABLE_PROPERY);
            errorProneOptions.getEnabled().set(false);
        } else {
            errorProneOptions.getEnabled().set(true);
        }

        errorProneOptions.getDisableWarningsInGeneratedCode().set(true);
        String projectPath = project.getProjectDir().getPath();
        String separator = Pattern.quote(Paths.get(projectPath).getFileSystem().getSeparator());
        errorProneOptions
                .getExcludedPaths()
                .set(String.format(
                        "%s%s(build|src%sgenerated.*)%s.*",
                        Pattern.quote(projectPath), separator, separator, separator));

        // FallThrough does not currently work with switch expressions
        // See https://github.com/google/error-prone/issues/1649
        errorProneOptions.check("FallThrough", project.provider(() -> {
            JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
            return ext.getSourceCompatibility().compareTo(JavaVersion.toVersion(14)) < 0
                    ? CheckSeverity.DEFAULT
                    : CheckSeverity.OFF;
        }));
        // UnnecessaryParentheses does not currently work with switch expressions
        errorProneOptions.check("UnnecessaryParentheses", project.provider(() -> {
            JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
            return ext.getSourceCompatibility().compareTo(JavaVersion.toVersion(14)) < 0
                    ? CheckSeverity.DEFAULT
                    : CheckSeverity.OFF;
        }));

        errorProneOptions.disable("CatchSpecificity", "UnusedVariable");
        errorProneOptions.error(
                "EqualsHashCode",
                "EqualsIncompatibleType",
                "StreamResourceLeak",
                "InputStreamSlowMultibyteRead",
                "JavaDurationGetSecondsGetNano",
                "URLEqualsHashCode");
        // Relax some checks for test code
        if (errorProneOptions.getCompilingTestOnlyCode().get()) {
            errorProneOptions.disable("UnnecessaryLambda");
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
                .map(ss -> filterOutPreconditions(
                        project.getConfigurations().getByName(ss.getCompileClasspathConfigurationName())))
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
        boolean hasPreconditions =
                hasDependenciesMatching(compileClasspath, BaselineErrorProne::isSafeLoggingPreconditionsDep);

        return check -> {
            if (!hasPreconditions) {
                if (check.equals("PreferSafeLoggingPreconditions")) {
                    log.info(
                            "Disabling check PreferSafeLoggingPreconditions as "
                                    + "'com.palantir.safe-logging:preconditions' missing from {}",
                            compileClasspath);
                    return false;
                }
                if (check.equals("PreferSafeLoggableExceptions")) {
                    log.info(
                            "Disabling check PreferSafeLoggableExceptions as 'com.palantir.safe-logging:preconditions' "
                                    + "missing from {}",
                            compileClasspath);
                    return false;
                }
            }
            return true;
        };
    }

    private static boolean isSafeLoggingPreconditionsDep(ModuleComponentIdentifier mci) {
        return mci.getGroup().equals("com.palantir.safe-logging")
                && mci.getModule().equals("preconditions");
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
        Map<String, CheckSeverity> checks = errorProneOptions.getChecks().get();
        return checks.get(check) == CheckSeverity.OFF
                || errorProneOptions.getErrorproneArgs().get().contains(String.format("-Xep:%s:OFF", check));
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
