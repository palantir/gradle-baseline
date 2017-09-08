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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.plugins.quality.Checkstyle;
import org.xml.sax.Attributes;

class CheckstyleReportHandler extends ReportHandler<Checkstyle> {

    private final List<Failure> failures = new ArrayList<>();
    private File file;

    @Override
    public void configureTask(Checkstyle task) {
        // Ensure XML output is enabled
        task.getReports().findByName("xml").setEnabled(true);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (qName) {
            case "file":
                file = new File(attributes.getValue("name"));
                break;

            case "error":
                failures.add(new Failure.Builder()
                        .source(attributes.getValue("source"))
                        .severity(attributes.getValue("severity").toUpperCase())
                        .file(file)
                        .line(Integer.parseInt(attributes.getValue("line")))
                        .message(attributes.getValue("message"))
                        .build());
                break;

            default:
                break;
        }
    }

    @Override
    public List<Failure> failures() {
        return failures;
    }
}
