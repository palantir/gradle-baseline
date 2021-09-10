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

package com.palantir.baseline

import com.palantir.baseline.plugins.BaselineScalastyle
import org.github.ngbinh.scalastyle.ScalaStylePlugin
import org.github.ngbinh.scalastyle.ScalaStyleTask
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

final class BaselineScalastyleTest extends Specification {
    private Project project

    def validScalaFile = '''
        package test;
        case class Test(field: Int)
        '''.stripIndent()

    def validJavaFile = '''
        package test;
        public class Test { }
        '''.stripIndent()

    def setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply 'scala'
        project.plugins.apply 'idea'
        project.plugins.apply BaselineScalastyle
    }

    def baselineCheckstylePluginApplied() {
        expect:
        project.plugins.hasPlugin(BaselineScalastyle.class)
    }

    def scalaStyleApplied() {
        expect:
        project.plugins.hasPlugin(ScalaStylePlugin.class)
    }

    def includesSourcests() {
        def file = new File(project.projectDir, 'src/main/scala/test/Test.scala')
        file.getParentFile().mkdirs()
        when:
        file << validScalaFile

        then:
        def tasks = project.tasks.withType(ScalaStyleTask.class)
        for (ScalaStyleTask task : tasks) {
            assert task.getSource().getFiles().contains(file)
        }
    }

    def configuresTargetJvmVersion() {
        expect:
        def tasks = project.tasks.withType(ScalaCompile.class)
        for (ScalaCompile task : tasks) {
            assert task.getScalaCompileOptions().getAdditionalParameters().contains("-target:jvm-1.8")
        }
    }

    def configuresScalaMixedMode() {
        def file = new File(project.projectDir, 'src/main/scala/test/Test.java')
        file.getParentFile().mkdirs()
        when:
        file << validJavaFile

        then:
        project.getRootProject()
                .getExtensions()
                .getByType(IdeaModel.class)
                .getProject()
                .getIpr()
                .withXml({XmlProvider xmlProvider ->
            assert xmlProvider.asString().contains("\"value\":\"Mixed\"")
        })
    }
}
