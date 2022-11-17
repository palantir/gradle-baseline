/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;

/** Configures the Gradle "checkstyle" task with Baseline settings. */
public final class BaselineCheckstyle extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        this.project = project;

        project.getPluginManager().apply(CheckstylePlugin.class);

        // Set default version (outside afterEvaluate so it can be overridden).
        project.getExtensions()
                .configure(CheckstyleExtension.class, ext -> ext.setToolVersion(getCheckstyleVersionFromResource()));

        // Configure checkstyle
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
            // We use the "JavadocMethod" module in our Checkstyle configuration, making
            // Java 8+ new doclint compiler feature redundant.
            if (javaConvention.getSourceCompatibility().isJava8Compatible()) {
                project.getTasks()
                        .withType(Javadoc.class)
                        .configureEach(javadoc ->
                                javadoc.options(javadocOptions -> ((StandardJavadocDocletOptions) javadocOptions)
                                        .addStringOption("Xdoclint:none", "-quiet")));
            }
        });

        project.getExtensions()
                .getByType(CheckstyleExtension.class)
                .getConfigDirectory()
                .set(this.project.file(Paths.get(getConfigDir(), "checkstyle").toString()));
        project.getPluginManager().withPlugin("eclipse", plugin -> {
            EclipseProject eclipseProject =
                    project.getExtensions().getByType(EclipseModel.class).getProject();
            eclipseProject.buildCommand("net.sf.eclipsecs.core.CheckstyleBuilder");
        });
    }

    // The idea is the checkstyle.version file can be more easily updated by excavator
    private static String getCheckstyleVersionFromResource() {
        URL url = Resources.getResource(BaselineCheckstyle.class, "/checkstyle.version");
        Preconditions.checkNotNull(url, "Unable to find 'checkstyle.version' resource");
        try {
            return Resources.toString(url, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Unable to lookup checkstyle version", e);
        }
    }
}
