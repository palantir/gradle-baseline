/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.github.sgtsilvio.gradle.proguard.ProguardPlugin;
import com.github.sgtsilvio.gradle.proguard.ProguardTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.internal.GFileUtils;

public final class BaselineDeadCode implements Plugin<Project> {

    private static final String DEAD_CODE_TASK_NAME = "deadCode";
    private static final String PROGUARD_TASK_NAME = "proguard";
    private static final String LOCKFILE = "baseline-dead-code.lock";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(ProguardPlugin.class);

        // TODO(dfox): emit a warning that this should not be applied to all projects, just your final distribution?

        TaskProvider<ProguardTask> proguardProvider = project.getTasks()
                .register(PROGUARD_TASK_NAME, ProguardTask.class, BaselineDeadCode::configureProguardTask);

        project.getTasks().register(DEAD_CODE_TASK_NAME, DefaultTask.class, task -> {
            configureLockfileTask(proguardProvider, task);
        });
    }

    private static void configureProguardTask(ProguardTask task) {
        Project project = task.getProject();
        Directory proguardDir =
                project.getLayout().getBuildDirectory().dir("proguard").get();
        Directory proguardOutDir = proguardDir.dir("out");
        task.getOutputs().dir(proguardDir);
        task.doFirst(__ -> {
            GFileUtils.deleteDirectory(proguardDir.getAsFile());
            try {
                Files.createDirectories(proguardDir.getAsFile().toPath());
                Files.createDirectories(proguardOutDir.getAsFile().toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // proguard has the concept of 'injars' which will be exhaustively analyzed looking for dead code, and
        // 'libraryjars' which must be present, but aren't exhaustively analuzed.
        // TODO(dfox): seems like immutables annotation processor can upset proguard... do we want the
        //  union of the compileOnly and runtimeClasspath configuration? Should this be user configurable?
        Configuration configuration = project.getConfigurations()
                .named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .get();
        Set<String> projectNames = project.getRootProject().getAllprojects().stream()
                .map(Project::getName)
                .collect(Collectors.toSet());
        Spec<File> fileSpec = file -> {
            return projectNames.stream()
                            .anyMatch(someProjectName -> file.getName().contains(someProjectName))
                    || file.isDirectory();
        };
        task.addInput(entry -> {
            // we want proguard to run on the class files produced by this project
            // TODO(dfox): could we grab these as .class files rather than by extracting a jar using variant
            //  aware selection?
            Task jarTask = project.getTasks().named(JavaPlugin.JAR_TASK_NAME).get();
            entry.getClasspath().from(jarTask.getOutputs().getFiles());

            // if we have any `project(':foo')` dependencies in the classpath, then proguard should also consider
            // these as 'inputjars' and analyze them (as any unused classes will be actionable).
            entry.getClasspath().from(configuration.filter(fileSpec));
        });
        task.addLibrary(entry -> {
            entry.getClasspath().from(configuration.filter(file -> !fileSpec.isSatisfiedBy(file)));
        });
        task.getJdkModules().add("java.base");

        // after proguard shrinks/optimizes the 'injars' it will write them to the configured output.
        task.addOutput(entry -> {
            entry.getDirectory().set(proguardOutDir);
        });

        List<String> rules = List.of(
                "-dontoptimize",
                "-dontobfuscate",
                "-keepclasseswithmembers public class * { public static void main(java.lang.String[]); }",
                "-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,LocalVariable*Table,*Annotation*,Synthetic,EnclosingMethod",
                "-keepparameternames",

                // proguard doesn't magically understand reflection, and all our jackson deserialization is done
                // using reflection. Hence, I'm just blanket keeping anything with a @JsonDeserialize annotation.
                // The downside is that this *might* be keeping unnecessary classes around.
                "-keep,includedescriptorclasses,includecode "
                        + "@com.fasterxml.jackson.databind.annotation.JsonDeserialize class **",
                "-keepclasseswithmembers,includedescriptorclasses,includecode class **$Json",
                "-keep class * { @com.fasterxml.jackson.annotation.JsonCreator <methods>; }",

                // The java compiler inlines primitive constants and String constants (static final fields).
                // ProGuard would therefore list such fields as not being used in the class files that it analyzes,
                // even if they are used in the source files
                "-keepclassmembers class * { static final % *; static final java.lang.String *; }",

                // helpful for debugging to see the full listing of injars and libraryjars.
                // note that this contains ABSOLUTE PATHS so probably isn't good for the build cache.
                "-printconfiguration " + proguardDir.file("printconfiguration"),

                // 'usage' means every class/field/method that proguard has deemed unused.
                "-printusage " + proguardDir.file("printusage"),

                // 'seeds' are classes which are deemed to be entrypoints (i.e. what was matched by the various
                // '-keep' options)
                "-printseeds " + proguardDir.file("printseeds"));
        task.getRules().addAll(rules);
    }

    private static void configureLockfileTask(TaskProvider<ProguardTask> proguardProvider, DefaultTask task) {
        Project project = task.getProject();
        ProguardTask proguardTask = proguardProvider.get();

        task.dependsOn(proguardProvider);

        Path lockfilePath = project.getProjectDir().toPath().resolve(LOCKFILE);
        task.getOutputs().file(lockfilePath).withPropertyName("lockfilePath");
        task.getInputs()
                .files(proguardTask.getInputClasspath())
                .withPropertyName("proguardInputClasspath")
                .withNormalizer(ClasspathNormalizer.class);
        task.getInputs()
                .files(proguardTask.getOutputClasspath())
                .withPropertyName("proguardOutputClaspath")
                .withNormalizer(ClasspathNormalizer.class);

        task.doLast(__ -> {
            Set<String> inputClasses = getClassNames(project, proguardTask.getInputClasspath());
            Set<String> outputClasses = getClassNames(project, proguardTask.getOutputClasspath());
            Set<String> deadClasses = inputClasses.stream()
                    .filter(s -> !outputClasses.contains(s))
                    .collect(Collectors.toUnmodifiableSet());

            Predicate<String> actionable = file -> {
                return !file.contains("$") && !file.contains("_");
            };

            // TODO(dfox): output the raw 'deadClasses' lines to a file in the build dir somewhere!
            List<String> lines = Stream.of(
                            Stream.of(
                                    "# Run ./gradlew " + task.getName() + " to regenerate this file",
                                    "# Classes with no known usages (likely actionable)"),
                            deadClasses.stream().sorted().filter(actionable),
                            Stream.of(
                                    "",
                                    "# Less actionable classes (likely generated): "
                                            + deadClasses.stream()
                                                    .filter(actionable.negate())
                                                    .count()))
                    .flatMap(s -> s)
                    .collect(Collectors.toList());

            DesiredLockfileState desiredLockfileState = deadClasses.isEmpty()
                    ? new DesiredLockfileState() {
                        @Override
                        public boolean verify() {
                            return !Files.exists(lockfilePath);
                        }

                        @Override
                        public void fix() throws IOException {
                            Files.delete(lockfilePath);
                        }
                    }
                    : new DesiredLockfileState() {
                        @Override
                        public boolean verify() throws IOException {
                            return Files.readAllLines(lockfilePath).equals(lines);
                        }

                        @Override
                        public void fix() throws IOException {
                            Files.write(
                                    lockfilePath,
                                    lines,
                                    StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                        }
                    };
            boolean runningOnCi = System.getenv("CI") != null;
            desiredLockfileState.run(runningOnCi);
        });
    }

    interface DesiredLockfileState {
        boolean verify() throws IOException;

        void fix() throws IOException;

        default void run(boolean runningOnCi) {
            try {
                if (!runningOnCi) {
                    fix();
                }

                if (!verify()) {
                    throw new GradleException(LOCKFILE + " is out of date, please re-run ./gradlew "
                            + DEAD_CODE_TASK_NAME + " to re-generate it");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Set<String> getClassNames(Project project, FileCollection classpath) {
        Set<String> classnames = new HashSet<>();
        classpath.getAsFileTree().forEach(file -> {
            if (file.getName().endsWith(".jar")) {
                project.zipTree(file).visit(details -> {
                    if (!details.isDirectory()) {
                        classnames.add(details.getPath());
                    }
                });
            } else {
                classnames.add(file.getName());
            }
        });
        return Collections.unmodifiableSet(classnames);
    }
}
