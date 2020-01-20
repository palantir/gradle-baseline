/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.palantir.baseline.plugins.BaselineFormat.FormatterState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;

/** Extracts Baseline configuration into the configuration directory. */
class BaselineConfig extends AbstractBaselinePlugin {

    public void apply(Project rootProject) {
        this.project = rootProject;

        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new IllegalArgumentException(
                    BaselineConfig.class.getCanonicalName() + " plugin can only be applied to the root project.");
        }

        Configuration configuration = rootProject.getConfigurations().create("baseline");

        // users can still override this default dependency, it just reduces boilerplate
        Optional<String> version = Optional.ofNullable(getClass().getPackage().getImplementationVersion());
        configuration.defaultDependencies(d -> d.add(rootProject
                .getDependencies()
                .create(String.format(
                        "com.palantir.baseline:gradle-baseline-java-config%s@zip",
                        version.map(v -> ":" + v).orElse("")))));

        // Create task for generating configuration.
        rootProject.getTasks().register("baselineUpdateConfig", task -> {
            task.setGroup("Baseline");
            task.setDescription("Installs or updates Baseline configuration files in .baseline/");
            task.getInputs().files(configuration);
            task.getOutputs().dir(getConfigDir());
            task.getOutputs().dir(rootProject.getRootDir().toPath().resolve("project"));
            task.doLast(new BaselineUpdateConfigAction(configuration, rootProject));
        });
    }

    private class BaselineUpdateConfigAction implements Action<Task> {
        private final Configuration configuration;
        private final Project rootProject;

        BaselineUpdateConfigAction(Configuration configuration, Project rootProject) {
            this.configuration = configuration;
            this.rootProject = rootProject;
        }

        @Override
        public void execute(Task task) {
            if (configuration.getFiles().size() != 1) {
                throw new IllegalArgumentException("Expected to find exactly one config dependency in the "
                        + "'baseline' configuration, found: "
                        + configuration.getFiles());
            }

            Path configDir = Paths.get(BaselineConfig.this.getConfigDir());
            rootProject.copy(copySpec -> {
                copySpec.from(rootProject.zipTree(configuration.getSingleFile()));
                copySpec.into(configDir);
                copySpec.exclude("**/scalastyle_config.xml");
                copySpec.setIncludeEmptyDirs(false);

                if (!BaselineFormat.eclipseFormattingEnabled(task.getProject())) {
                    copySpec.exclude("**/spotless/eclipse.xml");
                }
            });

            // Disable some checkstyle rules that clash with PJF
            if (BaselineFormat.palantirJavaFormatterState(rootProject) != FormatterState.OFF
                    || project.getPluginManager().hasPlugin("com.palantir.java-format")) {
                Path checkstyleXml = configDir.resolve("checkstyle/checkstyle.xml");

                try {
                    String contents = new String(Files.readAllBytes(checkstyleXml), StandardCharsets.UTF_8);
                    String replaced = contents.replace(
                            "        <module name=\"Indentation\"> "
                                    + "<!-- Java Style Guide: Block indentation: +4 spaces -->\n"
                                    + "            <property name=\"arrayInitIndent\" value=\"8\"/>\n"
                                    + "            <property name=\"lineWrappingIndentation\" value=\"8\"/>\n"
                                    + "        </module>\n",
                            "");
                    Preconditions.checkState(!contents.equals(replaced), "Patching checkstyle.xml must make a change");
                    Files.write(checkstyleXml, replaced.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException("Unable to patch " + checkstyleXml, e);
                }
            }

            if (rootProject.getAllprojects().stream()
                    .anyMatch(p -> p.getPluginManager().hasPlugin("scala")
                            && p.getPluginManager().hasPlugin("com.palantir.baseline-scalastyle"))) {
                // Matches intellij scala plugin settings per
                // https://github.com/JetBrains/intellij-scala/blob/baaa7c1dabe5222c4bca7c4dd8d80890ad2a8c6b/scala/scala-impl/src/org/jetbrains/plugins/scala/codeInspection/scalastyle/ScalastyleCodeInspection.scala#L19
                rootProject.copy(copySpec -> {
                    copySpec.from(
                            rootProject.zipTree(configuration.getSingleFile()).filter(file ->
                                    file.getName().equals("scalastyle_config.xml")));
                    copySpec.into(rootProject.getRootDir().toPath().resolve("project"));
                    copySpec.setIncludeEmptyDirs(false);
                });
            }
        }
    }
}
