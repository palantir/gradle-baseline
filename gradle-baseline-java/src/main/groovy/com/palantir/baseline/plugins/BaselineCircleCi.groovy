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
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.profile.BuildProfile
import org.gradle.profile.ProfileListener
import org.gradle.profile.ProfileReportRenderer

class BaselineCircleCi extends AbstractBaselinePlugin {
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")

    @Override
    void apply(Project project) {
        project.pluginManager.apply CircleStylePlugin
        final String circleArtifactsDir = System.getenv("CIRCLE_ARTIFACTS")
        if (circleArtifactsDir == null) {
            return
        }

        project.rootProject.allprojects({Project p ->
            p.getTasks().withType(Test.class, {Test test ->
                Path junitReportsDir = Paths.get(circleArtifactsDir, "junit")
                for (String component : test.getPath().substring(1).split(":")) {
                    junitReportsDir = junitReportsDir.resolve(component)
                }
                test.getReports().getHtml().setEnabled(true)
                test.getReports().getHtml().setDestination(junitReportsDir.toFile())
            })
        })

        if (project.getGradle().startParameter.profile) {
            project.getGradle().addListener(new ProfileListener() {
                @Override
                void buildFinished(BuildProfile buildProfile) {
                    ProfileReportRenderer renderer = new ProfileReportRenderer()
                    File file = Paths.get(circleArtifactsDir, "profile", "profile-" +
                            FILE_DATE_FORMAT.format(new Date(buildProfile.getBuildStarted())) + ".html").toFile()
                    renderer.writeTo(buildProfile, file)
                }
            })
        }
    }
}
