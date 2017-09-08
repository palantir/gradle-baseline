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

import org.gradle.api.plugins.quality.FindBugs;
import org.gradle.api.plugins.quality.FindBugsXmlReport;
import org.gradle.api.reporting.SingleFileReport;
import org.xml.sax.Attributes;

class FindBugsReportHandler extends ReportHandler<FindBugs> {

    private final List<String> sources = new ArrayList<>();
    private final List<Failure> failures = new ArrayList<>();
    private Failure.Builder failure = null;
    private StringBuilder content = null;
    private int depth = 0;

    @Override
    public void configureTask(FindBugs task) {
        for (SingleFileReport report : task.getReports()) {
            report.setEnabled(false);
        }
        FindBugsXmlReport xmlReport = (FindBugsXmlReport) task.getReports().findByName("xml");
        xmlReport.setEnabled(true);
        xmlReport.setWithMessages(true);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        depth++;
        switch (qName) {
            case "SrcDir":
                content = new StringBuilder();
                break;

            case "BugInstance":
                depth = 0;
                failure = new Failure.Builder()
                        .source(attributes.getValue("type"))
                        .severity("ERROR");
                break;

            case "LongMessage":
                content = new StringBuilder();
                break;

            case "SourceLine":
                if (depth == 1) {
                    String sourcepath = attributes.getValue("sourcepath");
                    File sourceFile = new File(sourcepath);
                    for (String source : sources) {
                        if (source.endsWith(sourcepath)) {
                            sourceFile = new File(source);
                        }
                    }
                    failure.file(sourceFile)
                            .line(Integer.parseInt(attributes.getValue("start")));
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (content != null) {
            content.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (qName) {
            case "SrcDir":
                sources.add(content.toString());
                content = null;
                break;

            case "LongMessage":
                failure.message(content.toString());
                content = null;
                break;

            case "BugInstance":
                failures.add(failure.build());
                failure = null;
                break;

            default:
                break;
        }
        depth--;
    }

    @Override
    public List<Failure> failures() {
        return failures;
    }
}
