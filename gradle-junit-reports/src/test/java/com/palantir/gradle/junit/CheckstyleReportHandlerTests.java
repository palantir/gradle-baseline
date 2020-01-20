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

import static com.palantir.gradle.junit.TestCommon.CHECKSTYLE_FAILURES;
import static com.palantir.gradle.junit.TestCommon.testFile;
import static com.palantir.gradle.junit.XmlUtils.parseXml;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class CheckstyleReportHandlerTests {

    @Test
    public void testNoErrors() throws IOException {
        List<Failure> failures = parseXml(
                        new CheckstyleReportHandler(),
                        testFile("no-failures-checkstyle.xml").openStream())
                .failures();
        assertThat(failures).isEmpty();
    }

    @Test
    public void testTwoErrors() throws IOException {
        List<Failure> failures = parseXml(
                        new CheckstyleReportHandler(),
                        testFile("two-namecheck-failures-checkstyle.xml").openStream())
                .failures();
        assertThat(failures).containsExactlyElementsOf(CHECKSTYLE_FAILURES);
    }
}
