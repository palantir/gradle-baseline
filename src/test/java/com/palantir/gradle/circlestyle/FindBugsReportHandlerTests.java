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

import static com.palantir.gradle.circlestyle.TestCommon.ROOT;
import static com.palantir.gradle.circlestyle.TestCommon.testFile;
import static com.palantir.gradle.circlestyle.XmlUtils.parseXml;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class FindBugsReportHandlerTests {

    private static final String DM_EXIT_MSG = " invokes System.exit(...), which shuts down the entire virtual machine";
    private static final File CLASSFILE = new File(ROOT, "src/main/java/com/example/MyClass.java");
    private static final List<Failure> FINDBUGS_FAILURES = ImmutableList.of(
            new Failure.Builder()
                    .source("DM_EXIT")
                    .severity("ERROR")
                    .file(CLASSFILE)
                    .line(5)
                    .message("com.example.MyClass.methodA()" + DM_EXIT_MSG)
                    .build(),
            new Failure.Builder()
                    .source("DM_EXIT")
                    .severity("ERROR")
                    .file(CLASSFILE)
                    .line(9)
                    .message("com.example.MyClass.methodB()" + DM_EXIT_MSG)
                    .build());

    @Test
    public void testNoErrors() throws IOException {
        List<Failure> failures =
                parseXml(new FindBugsReportHandler(), testFile("no-errors-findbugs.xml").openStream()).failures();
        assertThat(failures).isEmpty();
    }

    @Test
    public void testTwoErrors() throws IOException {
        List<Failure> failures =
                parseXml(new FindBugsReportHandler(), testFile("two-exit-errors-findbugs.xml").openStream()).failures();
        assertThat(failures).containsExactlyElementsOf(FINDBUGS_FAILURES);
    }

    /** @see <a href="https://github.com/palantir/gradle-circle-style/issues/7">Issue 7</a> */
    @Test
    public void testSyntheticSourceLine() throws IOException {
        parseXml(new FindBugsReportHandler(), testFile("synthetic-sourceline-findbugs.xml").openStream());
    }
}
