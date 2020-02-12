/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

/*
 * Original copyright:
 *
 * Copyright 2016 DiffPlug
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
package com.palantir.baseline.plugins;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.generic.LicenseHeaderStep;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.io.Serializable;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ensures java files begin with one of a number of approved copyright licenses. Inspired by spotless'
 * {@link LicenseHeaderStep}.
 */
final class MultiLicenseHeaderStep implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Note: don't change this so that we get the filtering out of bad files from
     * {@link LicenseHeaderStep#unsupportedJvmFilesFilter()} that {@link com.diffplug.gradle.spotless.JavaExtension} /
     * {@link com.diffplug.gradle.spotless.GroovyExtension} sets up for us.
     */
    private static final String NAME = "licenseHeader";

    /** Spotless will consider the license header to be the file prefix up to the first line starting with delimiter. */
    private static final Pattern DELIMITER_PATTERN =
            Pattern.compile("^(?! \\*|/\\*| \\*/)", Pattern.UNIX_LINES | Pattern.MULTILINE);

    private final List<LicenseHeader> licenseHeaders;

    private MultiLicenseHeaderStep(List<LicenseHeader> licenseHeaders) {
        this.licenseHeaders = licenseHeaders;
    }

    /** Creates a spotless {@link FormatterStep} which forces the start of each file to match a license header. */
    public static FormatterStep createFromHeaders(List<String> templates) {
        List<LicenseHeader> headers =
                templates.stream().map(LicenseHeader::fromTemplate).collect(Collectors.toList());
        return FormatterStep.create(
                MultiLicenseHeaderStep.NAME, new MultiLicenseHeaderStep(headers), step -> step::format);
    }

    public static String name() {
        return NAME;
    }

    private static class LicenseHeader implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final Pattern YEAR_RANGE = Pattern.compile("[0-9]{4}(-[0-9]{4})?");
        private static final String YEAR_TOKEN = "${today.year}";

        private final String licenseHeaderBeforeYearToken;
        private final String licenseHeaderAfterYearToken;

        private LicenseHeader(String licenseHeader) {
            Preconditions.checkArgument(
                    licenseHeader.contains(YEAR_TOKEN),
                    "License header must contain year token",
                    YEAR_TOKEN,
                    licenseHeader);
            int yearTokenIndex = licenseHeader.indexOf(YEAR_TOKEN);
            this.licenseHeaderBeforeYearToken = licenseHeader.substring(0, yearTokenIndex);
            this.licenseHeaderAfterYearToken = licenseHeader.substring(yearTokenIndex + YEAR_TOKEN.length());
        }

        static LicenseHeader fromTemplate(String template) {
            String unixEndings = LineEnding.toUnix(template.trim());
            Iterable<String> lines = Splitter.on('\n').split(unixEndings);
            String javadocHeader = Streams.stream(lines)
                    .map(line -> line.isEmpty() ? " *" : " * " + line)
                    .collect(Collectors.joining("\n"));
            return new LicenseHeader("/*\n" + javadocHeader + "\n */\n");
        }

        private boolean matches(String existingHeader) {
            int startOfTheSecondPart = existingHeader.indexOf(licenseHeaderAfterYearToken);
            return startOfTheSecondPart > licenseHeaderBeforeYearToken.length()
                    && (existingHeader.startsWith(licenseHeaderBeforeYearToken)
                            && startOfTheSecondPart + licenseHeaderAfterYearToken.length() == existingHeader.length())
                    && YEAR_RANGE
                            .matcher(existingHeader.substring(
                                    licenseHeaderBeforeYearToken.length(), startOfTheSecondPart))
                            .matches();
        }

        private String render(String existingHeader) {
            Matcher yearInfo = YEAR_RANGE.matcher(existingHeader);
            boolean existingHeaderContainsYear = yearInfo.find();
            String year = existingHeaderContainsYear
                    ? yearInfo.group(0)
                    : String.valueOf(YearMonth.now().getYear());
            return licenseHeaderBeforeYearToken + year + licenseHeaderAfterYearToken;
        }
    }

    /** Formats the given string. */
    public String format(String raw) {
        Matcher matcher = DELIMITER_PATTERN.matcher(raw);
        Preconditions.checkArgument(matcher.find(), "Raw input must match delimiter somewhere", DELIMITER_PATTERN, raw);
        String existingHeader = raw.substring(0, matcher.start());
        String rest = raw.substring(matcher.start());

        Optional<LicenseHeader> matchingHeader = licenseHeaders.stream()
                .filter(header -> header.matches(existingHeader))
                .findFirst();

        if (matchingHeader.isPresent()) {
            // the existing header was considered OK by one of our known licenses, so we leave it untouched
            return raw;
        }

        // None of our license patterns matched, so we'll replace it with the preferred one
        LicenseHeader preferredHeader = Iterables.getLast(licenseHeaders);
        return preferredHeader.render(existingHeader) + rest;
    }
}
