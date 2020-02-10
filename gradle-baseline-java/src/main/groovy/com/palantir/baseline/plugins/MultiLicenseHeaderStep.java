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
 *
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
import com.diffplug.spotless.SerializableFileFilter;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prefixes a license header before the package statement. */
public final class MultiLicenseHeaderStep implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String NAME = "licenseHeader";
    private static final String DEFAULT_YEAR_DELIMITER = "-";

    private static final SerializableFileFilter UNSUPPORTED_JVM_FILES_FILTER =
            SerializableFileFilter.skipFilesNamed("package-info.java", "package-info.groovy", "module-info.java");

    private final String licenseHeader;
    private final Pattern delimiterPattern;
    private Pattern yearMatcherPattern;
    private boolean hasYearToken;
    private String licenseHeaderBeforeYearToken;
    private String licenseHeaderAfterYearToken;
    private String licenseHeaderWithYearTokenReplaced;

    /** Creates a FormatterStep which forces the start of each file to match a license header. */
    public static FormatterStep createFromHeader(String licenseHeader, String delimiter) {
        return createFromHeader(licenseHeader, delimiter, DEFAULT_YEAR_DELIMITER);
    }

    public static FormatterStep createFromHeader(String licenseHeader, String delimiter, String yearSeparator) {
        Objects.requireNonNull(licenseHeader, "licenseHeader");
        Objects.requireNonNull(delimiter, "delimiter");
        Objects.requireNonNull(yearSeparator, "yearSeparator");
        return FormatterStep.create(
                MultiLicenseHeaderStep.NAME,
                new MultiLicenseHeaderStep(licenseHeader, delimiter, yearSeparator),
                step -> step::format);
    }

    public static String name() {
        return NAME;
    }

    /** The license that we'd like enforced. */
    private MultiLicenseHeaderStep(String licenseHeader, String delimiter, String yearSeparator) {
        if (delimiter.contains("\n")) {
            throw new IllegalArgumentException("The delimiter must not contain any newlines.");
        }
        // sanitize the input license
        licenseHeader = LineEnding.toUnix(licenseHeader);
        if (!licenseHeader.endsWith("\n")) {
            licenseHeader = licenseHeader + "\n";
        }
        this.licenseHeader = licenseHeader;
        this.delimiterPattern = Pattern.compile('^' + delimiter, Pattern.UNIX_LINES | Pattern.MULTILINE);
        this.hasYearToken = licenseHeader.contains("$YEAR");
        if (this.hasYearToken) {
            int yearTokenIndex = licenseHeader.indexOf("$YEAR");
            this.licenseHeaderBeforeYearToken = licenseHeader.substring(0, yearTokenIndex);
            this.licenseHeaderAfterYearToken = licenseHeader.substring(yearTokenIndex + 5);
            this.licenseHeaderWithYearTokenReplaced = licenseHeader.replace(
                    "$YEAR", String.valueOf(YearMonth.now().getYear()));
            this.yearMatcherPattern = Pattern.compile("[0-9]{4}(" + Pattern.quote(yearSeparator) + "[0-9]{4})?");
        }
    }

    /** Reads the license file from the given file. */
    private MultiLicenseHeaderStep(File licenseFile, Charset encoding, String delimiter, String yearSeparator)
            throws IOException {
        this(new String(Files.readAllBytes(licenseFile.toPath()), encoding), delimiter, yearSeparator);
    }

    /** Formats the given string. */
    public String format(String raw) {
        Matcher matcher = delimiterPattern.matcher(raw);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to find delimiter regex " + delimiterPattern);
        } else {
            if (hasYearToken) {
                if (matchesLicenseWithYearToken(raw, matcher)) {
                    // that means we have the license like `licenseHeaderBeforeYearToken 1990-2015
                    // licenseHeaderAfterYearToken`
                    return raw;
                } else {
                    return licenseHeaderWithYearTokenReplaced + raw.substring(matcher.start());
                }
            } else if (matcher.start() == licenseHeader.length() && raw.startsWith(licenseHeader)) {
                // if no change is required, return the raw string without
                // creating any other new strings for maximum performance
                return raw;
            } else {
                // otherwise we'll have to add the header
                return licenseHeader + raw.substring(matcher.start());
            }
        }
    }

    private boolean matchesLicenseWithYearToken(String raw, Matcher matcher) {
        int startOfTheSecondPart = raw.indexOf(licenseHeaderAfterYearToken);
        return startOfTheSecondPart > licenseHeaderBeforeYearToken.length()
                && (raw.startsWith(licenseHeaderBeforeYearToken)
                        && startOfTheSecondPart + licenseHeaderAfterYearToken.length() == matcher.start())
                && yearMatcherPattern
                        .matcher(raw.substring(licenseHeaderBeforeYearToken.length(), startOfTheSecondPart))
                        .matches();
    }
}
