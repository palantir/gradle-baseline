/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.baseline.plugins

import com.palantir.gradle.circlestyle.CircleStylePlugin
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.testing.Test

class BaselineCircleCi extends AbstractBaselinePlugin {
    @Override
    void apply(Project project) {
        project.pluginManager.apply CircleStylePlugin
        final String circleArtifactsDir = System.getenv("CIRCLE_ARTIFACTS")
        if (circleArtifactsDir == null) {
            return
        }

        project.rootProject.allprojects({Project p ->
            p.getTasks().withType(Test.class, {Test test ->
                File junitReportsDir = new File(circleArtifactsDir, "junit")
                for (String component : test.getPath().substring(1).split(":")) {
                    junitReportsDir = new File(junitReportsDir, component)
                }
                test.getReports().getHtml().setEnabled(true)
                test.getReports().getHtml().setDestination(junitReportsDir)
            })
        })
        project.getGradle().buildFinished({BuildResult result ->
            File profileDir = new File(project.getRootProject().getBuildDir(), "reports/profile")
            if (profileDirexists()) {
                project.getRootProject().copy({CopySpec copySpec ->
                    copySpec.into(new File(circleArtifactsDir, "profile"))
                    copySpec.from(profileDir)
                })
            }
        })
    }
}
