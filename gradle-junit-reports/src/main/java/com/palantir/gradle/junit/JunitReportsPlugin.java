/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.junit;

import com.google.common.base.Splitter;
import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

public final class JunitReportsPlugin implements Plugin<Project> {

    @Override
    @SuppressWarnings("Slf4jLogsafeArgs")
    public void apply(Project project) {
        JunitReportsExtension rootExt = project.getRootProject().getExtensions().getByType(JunitReportsExtension.class);
        JunitTaskResultExtension ext = JunitTaskResultExtension.register(project);

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.getReports().getJunitXml().getRequired().set(true);
            test.getReports()
                    .getJunitXml()
                    .getOutputLocation()
                    .fileProvider(junitPath(rootExt.getReportsDirectory(), test.getPath()));
        });

        project.getTasks().withType(Checkstyle.class).configureEach(checkstyle -> {
            ext.registerTask(
                    checkstyle.getName(), XmlReportFailuresSupplier.create(checkstyle, new CheckstyleReportHandler()));
        });

        project.getTasks().withType(JavaCompile.class).configureEach(javac -> {
            ext.registerTask(javac.getName(), JavacFailuresSupplier.create(javac));
        });

        ext.getTaskEntries()
                .configureEach(entry -> JunitReportsFinalizer.registerFinalizer(
                        project,
                        entry.name(),
                        rootExt.getTaskTimer(),
                        entry.failuresSupplier(),
                        rootExt.getReportsDirectory()));
    }

    private static Provider<File> junitPath(DirectoryProperty basePath, String testPath) {
        return basePath.map(dir -> dir.dir("junit"))
                .map(dir ->
                        dir.file(String.join(File.separator, Splitter.on(':').splitToList(testPath.substring(1)))))
                .map(RegularFile::getAsFile);
    }
}
