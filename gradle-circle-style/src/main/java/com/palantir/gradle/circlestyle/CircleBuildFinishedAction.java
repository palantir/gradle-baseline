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
package com.palantir.gradle.circlestyle;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.TransformerException;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.w3c.dom.Document;

public final class CircleBuildFinishedAction implements Action<BuildResult> {

    private final Integer container;
    private final Path targetFile;
    private final long startTimeNanos;
    private final CircleBuildFailureListener failureListener;

    public CircleBuildFinishedAction(Integer container, Path targetFile, CircleBuildFailureListener failureListener) {
        this.container = container;
        this.targetFile = targetFile;
        this.failureListener = failureListener;
        startTimeNanos = System.nanoTime();
    }

    @Override
    public void execute(BuildResult result) {
        String name = (container != null) ? "container " + container : "gradle";
        Report report = new Report.Builder()
                .name(name)
                .subname(name)
                .elapsedTimeNanos(System.nanoTime() - startTimeNanos)
                .addAllTestCases(failureListener.getTestCases())
                .build();
        Document xml = JUnitReportCreator.reportToXml(report);

        try {
            Files.createDirectories(targetFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            XmlUtils.write(writer, xml);
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
