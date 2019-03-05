/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.junit;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.TransformerException;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.w3c.dom.Document;

public final class BuildFinishedAction implements Action<BuildResult> {

    private final Provider<RegularFile> targetFile;
    private final long startTimeNanos;
    private final BuildFailureListener failureListener;

    public BuildFinishedAction(Provider<RegularFile> targetFile, BuildFailureListener failureListener) {
        this.targetFile = targetFile;
        this.failureListener = failureListener;
        startTimeNanos = System.nanoTime();
    }

    @Override
    public void execute(BuildResult result) {
        Report report = new Report.Builder()
                .name("gradle")
                .subname("gradle")
                .elapsedTimeNanos(System.nanoTime() - startTimeNanos)
                .addAllTestCases(failureListener.getTestCases())
                .build();
        Document xml = JunitReportCreator.reportToXml(report);

        try {
            Files.createDirectories(getTargetFile().getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = Files.newBufferedWriter(getTargetFile(), StandardCharsets.UTF_8)) {
            XmlUtils.write(writer, xml);
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getTargetFile() {
        return targetFile.get().getAsFile().toPath();
    }
}
