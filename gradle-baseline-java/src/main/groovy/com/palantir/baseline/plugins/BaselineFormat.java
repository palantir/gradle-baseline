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

import com.diffplug.gradle.spotless.SpotlessExtension;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.Project;

class BaselineFormat extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        if (!project.getPluginManager().hasPlugin("java")) {
            project.getLogger().warn("com.palantir.baseline-format should not be applied to non-java project: {}",
                    project.getName());
            return;
        }

        project.getPluginManager().apply("com.diffplug.gradle.spotless");

        Path configFile = Paths.get(getConfigDir(), "spotless", "spotless.eclipse.xml");
        if (!configFile.toFile().exists()) {
            project.getLogger().error("Config file must exist, try ./gradlew baselineUpdateConfig: {}", configFile);
            return;
        }

        project.getExtensions().getByType(SpotlessExtension.class).java(java -> {
            // TODO(dfox): apply this to all source sets, not just 'main' and 'test'
            java.target("src/main/java/**/*.java", "src/main/test/**/*.java");
            java.eclipse().configFile(configFile);
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
            java.indentWithSpaces(4);
            java.endWithNewline();
        });
        
        project.getTasks().getByName("spotlessCheck", task -> task.setEnabled(false));
    }
}
