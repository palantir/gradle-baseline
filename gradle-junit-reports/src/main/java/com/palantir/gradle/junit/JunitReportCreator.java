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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class JunitReportCreator {

    static Document reportToXml(Report report) {
        try {
            Document xml = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .newDocument();
            String elapsedTimeString = String.format("%.03f", report.elapsedTimeNanos() / 1e9);

            Element testSuitesXml = xml.createElement("testsuites");
            xml.appendChild(testSuitesXml);
            testSuitesXml.setAttribute("id", asId(report.name()));
            testSuitesXml.setAttribute("name", report.name());
            testSuitesXml.setAttribute(
                    "tests", Integer.toString(report.testCases().size()));
            testSuitesXml.setAttribute("time", elapsedTimeString);

            Element testSuiteXml = xml.createElement("testsuite");
            testSuitesXml.appendChild(testSuiteXml);
            testSuiteXml.setAttribute("id", asId(report.subname()));
            testSuiteXml.setAttribute("name", report.subname());
            testSuiteXml.setAttribute(
                    "tests", Integer.toString(report.testCases().size()));
            testSuiteXml.setAttribute("time", elapsedTimeString);

            int failures = 0;
            for (Report.TestCase testCase : report.testCases()) {
                Element testCaseXml = xml.createElement("testcase");
                testSuiteXml.appendChild(testCaseXml);
                testCaseXml.setAttribute("id", asId(testCase.name()));
                testCaseXml.setAttribute("name", testCase.name());

                Report.Failure failure = testCase.failure();
                if (failure != null) {
                    failures++;
                    Element failureXml = xml.createElement("failure");
                    testCaseXml.appendChild(failureXml);
                    failureXml.setAttribute("message", failure.message());
                    failureXml.setAttribute("type", "ERROR");
                    failureXml.setTextContent(failure.details());
                }
            }

            testSuitesXml.setAttribute("failures", Integer.toString(failures));
            testSuiteXml.setAttribute("failures", Integer.toString(failures));

            return xml;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String asId(String name) {
        return name.replace(" - ", ".");
    }

    private JunitReportCreator() {}
}
