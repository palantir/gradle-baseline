/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Splitter;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.tasks.compile.JavaCompile;

public final class JavacFailuresSupplier implements FailuresSupplier {

    public static JavacFailuresSupplier create(JavaCompile javac) {
        // Capture standard output
        final StringBuilder errorStream = new StringBuilder();
        javac.getLogging().addStandardErrorListener(errorStream::append);

        // Configure the finalizer task
        return new JavacFailuresSupplier(errorStream);
    }

    private static final Pattern ERROR_LINE = Pattern.compile("([^ ].*):(\\d+): error: (.*)");

    private final StringBuilder errorStream;

    JavacFailuresSupplier(StringBuilder errorStream) {
        this.errorStream = errorStream;
    }

    @Override
    public List<Failure> getFailures() {
        List<Failure> failures = new ArrayList<>();
        Failure.Builder failureBuilder = null;
        StringBuilder details = null;
        for (String line : Splitter.on("\n").split(errorStream.toString())) {
            if (failureBuilder != null) {
                if (line.startsWith(" ")) {
                    details.append("\n").append(line);
                    continue;
                } else {
                    failures.add(failureBuilder.details(details.toString()).build());
                    failureBuilder = null;
                    details = null;
                }
            }
            Matcher matcher = ERROR_LINE.matcher(line);
            if (matcher.matches()) {
                failureBuilder = new Failure.Builder()
                        .file(new File(matcher.group(1)))
                        .line(Integer.parseInt(matcher.group(2)))
                        .severity("ERROR")
                        .message(matcher.group(3));
                details = new StringBuilder();
            }
        }
        if (failureBuilder != null) {
            failures.add(failureBuilder.details(details.toString()).build());
        }
        return failures;
    }

    @Override
    public RuntimeException handleInternalFailure(Path reportDir, RuntimeException ex) {
        return ex;
    }
}
