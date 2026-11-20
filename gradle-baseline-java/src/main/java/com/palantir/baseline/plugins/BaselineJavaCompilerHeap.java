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

import java.util.Collections;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Configures the memory of forked {@link JavaCompile} compiler daemons.
 *
 * <ul>
 *   <li>Increases the default heap from {@code 512m} to {@code 2g}.</li>
 *   <li>Makes idle compiler daemons return unused heap to the OS. Gradle keeps compiler daemons alive for the rest of
 *       the build, and by default G1 only gives memory back after a GC, which an idle daemon never triggers. See
 *       {@link PeriodicGcArgumentProvider}.</li>
 * </ul>
 */
public final class BaselineJavaCompilerHeap implements Plugin<Project> {

    private static final String JAVAC_HEAP = "2g";

    @Override
    public void apply(Project proj) {
        proj.afterEvaluate(
                project -> project.getTasks().withType(JavaCompile.class).configureEach(javaCompileTask -> {
                    ForkOptions options = javaCompileTask.getOptions().getForkOptions();
                    if (options.getMemoryMaximumSize() == null) {
                        options.setMemoryMaximumSize(JAVAC_HEAP);
                    }
                    options.getJvmArgumentProviders()
                            .add(new PeriodicGcArgumentProvider(compilerJavaVersion(javaCompileTask)));
                }));
    }

    /** The major version of the JVM that runs this task's compiler: its toolchain, else the JVM running Gradle. */
    private static Provider<Integer> compilerJavaVersion(JavaCompile task) {
        return task.getJavaCompiler()
                .map(compiler -> compiler.getMetadata().getLanguageVersion().asInt())
                .orElse(task.getProject()
                        .provider(() -> Integer.parseInt(JavaVersion.current().getMajorVersion())));
    }

    /**
     * Adds {@code -XX:G1PeriodicGCInterval}, which makes an idle G1 JVM run a GC after the interval and uncommit unused
     * heap. Measured on a large build, idle compiler daemons dropped from about 2 GB to about 0.5 GB resident.
     *
     * <p>The flag only affects G1, the default collector on machines with at least 2 CPUs and about 1.8 GB of memory.
     * Other collectors accept and ignore it; ZGC and Shenandoah already uncommit idle memory by default. It only exists
     * from JDK 12 (JEP 346): older JVMs refuse to start with it, so it is only added when the compiler runs on JDK 12+.
     */
    static final class PeriodicGcArgumentProvider implements CommandLineArgumentProvider {

        static final String FLAG = "-XX:G1PeriodicGCInterval=5000";
        static final int MINIMUM_JAVA_VERSION = 12;

        private final Provider<Integer> compilerJavaVersion;

        PeriodicGcArgumentProvider(Provider<Integer> compilerJavaVersion) {
            this.compilerJavaVersion = compilerJavaVersion;
        }

        @Override
        public Iterable<String> asArguments() {
            return compilerJavaVersion.get() >= MINIMUM_JAVA_VERSION
                    ? Collections.singletonList(FLAG)
                    : Collections.emptyList();
        }
    }
}
