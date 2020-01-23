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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import groovy.xml.QName;
import java.nio.file.Paths;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.github.ngbinh.scalastyle.ScalaStylePlugin;
import org.github.ngbinh.scalastyle.ScalaStyleTask;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.plugins.ide.idea.model.IdeaModel;

public final class BaselineScalastyle extends AbstractBaselinePlugin {
    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().withPlugin("scala", plugin -> {
            project.getPluginManager().withPlugin("nebula.dependency-recommender", nebulaPlugin ->
                    project.getExtensions().configure(RecommendationProviderContainer.class, recommendations ->
                            recommendations.excludeConfigurations("zinc")));
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
            project.getTasks().withType(ScalaCompile.class).configureEach(scalaCompile -> scalaCompile
                    .getScalaCompileOptions()
                    .setAdditionalParameters(ImmutableList.of("-target:jvm-"
                            + javaConvention.getTargetCompatibility().toString())));
            project.getRootProject().getPluginManager().withPlugin("idea", ideaPlugin ->
                    project.getRootProject().getExtensions().configure(IdeaModel.class, ideaModel ->
                            configureIdeaPlugin(
                                    ideaModel,
                                    javaConvention
                                            .getSourceSets()
                                            .named(SourceSet.MAIN_SOURCE_SET_NAME)
                                            .get(),
                                    javaConvention.getTargetCompatibility().toString())));
            project.getPluginManager().apply(ScalaStylePlugin.class);
            TaskCollection<ScalaStyleTask> scalaStyleTasks = project.getTasks().withType(ScalaStyleTask.class);
            scalaStyleTasks.configureEach(scalaStyleTask -> {
                scalaStyleTask.setConfigLocation(project.getRootDir()
                        .toPath()
                        .resolve(Paths.get("project", "scalastyle_config.xml"))
                        .toString());
                scalaStyleTask.setIncludeTestSourceDirectory(true);
                scalaStyleTask.setFailOnWarning(true);
                javaConvention.getSourceSets().forEach(sourceSet ->
                        sourceSet.getAllSource().getSrcDirs().forEach(resourceDir ->
                                scalaStyleTask.source(resourceDir.toString())));
            });
            project.getTasks().named("check").configure(task -> task.dependsOn(scalaStyleTasks));
        });
    }

    private void configureIdeaPlugin(IdeaModel ideaModel, SourceSet mainSourceSet, String javaVersion) {
        Convention scalaConvention = (Convention) InvokerHelper.getProperty(mainSourceSet, "convention");
        ScalaSourceSet scalaSourceSet = scalaConvention.getPlugin(ScalaSourceSet.class);
        // If scala source directory doesn't contain java files use "JavaThenScala" compilation mode
        String compilerMode = scalaSourceSet
                        .getScala()
                        .filter(file -> file.getName().endsWith("java"))
                        .isEmpty()
                ? "JavaThenScala"
                : "Mixed";
        ideaModel.getProject().getIpr().withXml(xmlProvider -> {
            // configure target jvm mode
            String targetJvmVersion = "-target:jvm-" + javaVersion;
            Node rootNode = xmlProvider.asNode();
            Node scalaCompilerConf = (Node) rootNode.getAt(new QName("component")).stream()
                    .filter(o -> ((Node) o).attributes().get("name").equals("ScalaCompilerConfiguration"))
                    .findFirst()
                    .orElseGet(() ->
                            rootNode.appendNode("component", ImmutableMap.of("name", "ScalaCompilerConfiguration")));
            // configure scala compilation order
            Node compilerOrder = (Node) scalaCompilerConf.getAt(new QName("option")).stream()
                    .filter(o -> ((Node) o).attributes().get("name").equals("compileOrder"))
                    .findFirst()
                    .orElseGet(() -> scalaCompilerConf.appendNode("option"));
            compilerOrder.attributes().put("name", "compileOrder");
            compilerOrder.attributes().put("value", compilerMode);
            Node parametersNode = (Node) scalaCompilerConf.getAt(new QName("parameters")).stream()
                    .findFirst()
                    .orElseGet(() -> scalaCompilerConf.appendNode("parameters"));
            Node parameter = (Node) parametersNode.getAt(new QName("parameter")).stream()
                    .filter(o -> ((Node) o).attributes().get("value").equals(targetJvmVersion))
                    .findFirst()
                    .orElseGet(() -> parametersNode.appendNode("parameter"));
            parameter.attributes().put("value", targetJvmVersion);
        });
    }
}
