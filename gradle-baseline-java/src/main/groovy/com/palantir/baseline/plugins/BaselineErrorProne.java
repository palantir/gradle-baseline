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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class BaselineErrorProne implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineErrorProne.class);
    public static final String EXTENSION_NAME = "baselineErrorProne";
    private static final String PROP_ERROR_PRONE_APPLY = "errorProneApply";
    private static final String SUPPRESS_STAGE_ONE = "errorProneSuppressStage1";
    private static final String SUPPRESS_STAGE_TWO = "errorProneSuppressStage2";
    private static final String DISABLE_PROPERTY = "com.palantir.baseline-error-prone.disable";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private static void setupTransform(Project project) {
        Attribute<Boolean> suppressiblified =
                Attribute.of("com.palantir.baseline.errorprone.suppressiblified", Boolean.class);
        project.getDependencies().getAttributesSchema().attribute(suppressiblified);
        project.getDependencies()
                .getArtifactTypes()
                .getByName("jar")
                .getAttributes()
                .attribute(suppressiblified, false);

        project.getConfigurations().named("annotationProcessor").configure(errorProneConfiguration -> {
            errorProneConfiguration.getAttributes().attribute(suppressiblified, true);
        });

        project.getDependencies().registerTransform(Suppressiblify.class, spec -> {
            spec.getParameters().getCacheBust().set(UUID.randomUUID().toString());
            Attribute<String> artifactType = Attribute.of("artifactType", String.class);
            spec.getFrom().attribute(suppressiblified, false).attribute(artifactType, "jar");
            spec.getTo().attribute(suppressiblified, true).attribute(artifactType, "jar");
        });
    }

    public abstract static class SParams implements TransformParameters {
        @Input
        public abstract Property<String> getCacheBust();
    }

    public abstract static class Suppressiblify implements TransformAction<SParams> {
        private static final Logger logger = Logging.getLogger(Suppressiblify.class);
        private static final String BUG_CHECKER = "com/google/errorprone/bugpatterns/BugChecker";
        private static final String SUPPRESSIBLE_BUG_CHECKER =
                "com/palantir/baseline/errorprone/SuppressibleBugChecker";

        @InputArtifact
        protected abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public final void transform(TransformOutputs outputs) {
            File output = outputs.file(getInputArtifact().get().getAsFile().getName());

            ClassFileVisitor hasBugChecker = (jarEntry, classReader) -> {
                return !BUG_CHECKER.equals(classReader.getSuperName());
            };

            if (visitClassFiles(hasBugChecker)) {
                try {
                    Files.copy(getInputArtifact().get().getAsFile().toPath(), output.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            try (ZipOutputStream zipOutputStream =
                    new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
                visitClassFiles(new ClassFileVisitor() {
                    @Override
                    public boolean continueAfterReading(JarEntry jarEntry, ClassReader classReader) {
                        ClassWriter classWriter = new ClassWriter(classReader, 0);
                        SuppressifyingClassVisitor suppressifyingClassVisitor =
                                new SuppressifyingClassVisitor(Opcodes.ASM9, classWriter);
                        classReader.accept(suppressifyingClassVisitor, 0);
                        byte[] newClassBytes = classWriter.toByteArray();

                        jarEntry.setSize(newClassBytes.length);
                        jarEntry.setCompressedSize(-1);
                        try {
                            zipOutputStream.putNextEntry(jarEntry);
                            zipOutputStream.write(newClassBytes);
                            zipOutputStream.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    }

                    @Override
                    public void visitNonClassFile(JarEntry jarEntry, InputStream inputStream) {
                        try {
                            zipOutputStream.putNextEntry(jarEntry);
                            inputStream.transferTo(zipOutputStream);
                            zipOutputStream.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        interface ClassFileVisitor {
            default void visitNonClassFile(JarEntry jarEntry, InputStream inputStream) {}

            boolean continueAfterReading(JarEntry jarEntry, ClassReader classReader);
        }

        private boolean visitClassFiles(ClassFileVisitor classFileVisitor) {
            try (JarFile jarFile = new JarFile(getInputArtifact().get().getAsFile())) {
                long totalEntriesVisited = jarFile.stream()
                        .takeWhile(jarEntry -> {
                            try {
                                if (!jarEntry.getName().endsWith(".class")) {
                                    classFileVisitor.visitNonClassFile(jarEntry, jarFile.getInputStream(jarEntry));
                                    return true;
                                }

                                ClassReader classReader = new ClassReader(jarFile.getInputStream(jarEntry));
                                return classFileVisitor.continueAfterReading(jarEntry, classReader);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .count();

                return totalEntriesVisited == jarFile.size();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static final class SuppressifyingClassVisitor extends ClassVisitor {
            private boolean isBugCheckerWeWantToChange = false;

            protected SuppressifyingClassVisitor(int api, ClassVisitor classVisitor) {
                super(api, classVisitor);
            }

            @Override
            public void visit(
                    int version, int access, String name, String signature, String superName, String[] interfaces) {
                isBugCheckerWeWantToChange = !name.equals(SUPPRESSIBLE_BUG_CHECKER) && superName.equals(BUG_CHECKER);

                super.visit(
                        version,
                        access,
                        name,
                        signature,
                        isBugCheckerWeWantToChange ? SUPPRESSIBLE_BUG_CHECKER : superName,
                        interfaces);
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (isBugCheckerWeWantToChange && "<init>".equals(name)) {
                    return new SuppressifyingMethodVisitor(Opcodes.ASM9, methodVisitor);
                }

                return methodVisitor;
            }
        }

        private static final class SuppressifyingMethodVisitor extends MethodVisitor {
            protected SuppressifyingMethodVisitor(int api, MethodVisitor methodVisitor) {
                super(api, methodVisitor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                // Modify the BugChecker superclass constructor call to call the new superclass constructor
                if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name) && owner.equals(BUG_CHECKER)) {
                    super.visitMethodInsn(opcode, SUPPRESSIBLE_BUG_CHECKER, name, descriptor, isInterface);
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }

    private static void applyToJavaProject(Project project) {
        BaselineErrorProneExtension errorProneExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineErrorProneExtension.class, project);
        project.getPluginManager().apply(ErrorPronePlugin.class);

        String version = Optional.ofNullable((String) project.findProperty("baselineErrorProneVersion"))
                .or(() -> Optional.ofNullable(
                        BaselineErrorProne.class.getPackage().getImplementationVersion()))
                .orElseThrow(() -> new RuntimeException("BaselineErrorProne implementation version not found"));

        project.getDependencies()
                .add(ErrorPronePlugin.CONFIGURATION_NAME, "com.palantir.baseline:baseline-error-prone:" + version);

        setupTransform(project);

        if (project.hasProperty(SUPPRESS_STAGE_TWO)) {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                project.getDependencies()
                        .add(
                                sourceSet.getCompileOnlyConfigurationName(),
                                "com.palantir.baseline:suppressible-errorprone-annotations:" + version);
            });
        }

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        configureErrorProneOptions(project, errorProneExtension, javaCompile, errorProneOptions);
                    });
        });

        // To allow refactoring of deprecated methods, even when -Xlint:deprecation is specified, we need to remove
        // these compiler flags after all configuration has happened.
        project.afterEvaluate(
                unused -> project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                    if (isErrorProneRefactoring(project)
                            || project.hasProperty(SUPPRESS_STAGE_ONE)
                            || project.hasProperty(SUPPRESS_STAGE_TWO)) {
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

        if (isErrorProneRefactoring(project)
                || project.hasProperty(SUPPRESS_STAGE_ONE)
                || project.hasProperty(SUPPRESS_STAGE_TWO)) {
            // Don't attempt to cache since it won't capture the source files that might be modified
            javaCompile.getOutputs().cacheIf(t -> false);
        }

        if (project.hasProperty(SUPPRESS_STAGE_ONE)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepOpt:" + SUPPRESS_STAGE_ONE + "=true");
                }
            });
        }

        if (project.hasProperty(SUPPRESS_STAGE_TWO)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepPatchLocation:IN_PLACE", "-XepPatchChecks:SuppressWarningsCoalesce");
                }
            });
        }

        if (isErrorProneRefactoring(project) || project.hasProperty(SUPPRESS_STAGE_ONE)) {
            // TODO(gatesn): Is there a way to discover error-prone checks?
            // Maybe service-load from a ClassLoader configured with annotation processor path?
            // https://github.com/google/error-prone/pull/947
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                // intentionally not using a lambda to reduce gradle warnings
                @Override
                public Iterable<String> asArguments() {
                    Optional<List<String>> specificChecks = getSpecificErrorProneChecks(project);
                    if (specificChecks.isPresent()) {
                        List<String> errorProneChecks = specificChecks.get();
                        // Work around https://github.com/google/error-prone/issues/3908 by explicitly enabling any
                        // check we want to use patch checks for (ensuring it is not disabled); if this is fixed, the
                        // -Xep:*:ERROR arguments could be removed
                        return Iterables.concat(
                                errorProneChecks.stream()
                                        .map(checkName -> "-Xep:" + checkName + ":ERROR")
                                        .collect(Collectors.toList()),
                                ImmutableList.of(
                                        "-XepPatchChecks:" + Joiner.on(',').join(errorProneChecks),
                                        "-XepPatchLocation:IN_PLACE"));
                    } else {
                        Optional<SourceSet> maybeSourceSet = project
                                .getConvention()
                                .getPlugin(JavaPluginConvention.class)
                                .getSourceSets()
                                .matching(ss -> javaCompile.getName().equals(ss.getCompileJavaTaskName()))
                                .stream()
                                .collect(MoreCollectors.toOptional());

                        // Don't apply checks that have been explicitly disabled
                        Stream<String> errorProneChecks = getNotDisabledErrorproneChecks(
                                project, errorProneExtension, javaCompile, maybeSourceSet, errorProneOptions);
                        return ImmutableList.of(
                                "-XepPatchChecks:" + Joiner.on(',').join(errorProneChecks.iterator()),
                                "-XepPatchLocation:IN_PLACE");
                    }
                }
            });
        }
    }

    static String excludedPathsRegex() {
        // Error-prone normalizes filenames to use '/' path separator:
        // https://github.com/google/error-prone/blob/c601758e81723a8efc4671726b8363be7a306dce
        // /check_api/src/main/java/com/google/errorprone/util/ASTHelpers.java#L1277-L1285
        return ".*/(build|generated_.*[sS]rc|src/generated.*)/.*";
    }

    private static Optional<List<String>> getSpecificErrorProneChecks(Project project) {
        return Optional.ofNullable(project.findProperty(PROP_ERROR_PRONE_APPLY))
                .map(Objects::toString)
                .flatMap(value -> Optional.ofNullable(Strings.emptyToNull(value)))
                .map(value -> Splitter.on(',').trimResults().omitEmptyStrings().splitToList(value))
                .flatMap(list -> list.isEmpty() ? Optional.empty() : Optional.of(list));
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

    private static boolean isErrorProneRefactoring(Project project) {
        return project.hasProperty(PROP_ERROR_PRONE_APPLY);
    }

    private static boolean isDisabled(Project project) {
        Object disable = project.findProperty(DISABLE_PROPERTY);
        if (disable == null) {
            return false;
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
