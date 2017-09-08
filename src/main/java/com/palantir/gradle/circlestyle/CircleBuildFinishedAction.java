/*
 * Copyright 2017 Palantir Technologies, Inc.
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

import static com.palantir.gradle.circlestyle.JUnitReportCreator.reportToXml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.w3c.dom.Document;

class CircleBuildFinishedAction implements Action<BuildResult> {

    private final Integer container;
    private final File targetFile;
    private final long startTimeNanos;
    private final CircleBuildFailureListener failureListener;

    CircleBuildFinishedAction(Integer container, File targetFile, CircleBuildFailureListener failureListener) {
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
        Document xml = reportToXml(report);

        targetFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(targetFile)) {
            XmlUtils.write(writer, xml);
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
