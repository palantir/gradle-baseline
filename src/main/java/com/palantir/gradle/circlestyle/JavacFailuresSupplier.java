package com.palantir.gradle.circlestyle;

import static java.lang.Integer.parseInt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.compile.JavaCompile;

class JavacFailuresSupplier implements FailuresSupplier {

    public static JavacFailuresSupplier create(final JavaCompile javac) {
        // Capture standard output
        final StringBuilder errorStream = new StringBuilder();
        StandardOutputListener listener = new StandardOutputListener() {
            @Override
            public void onOutput(CharSequence output) {
                errorStream.append(output);
            }
        };
        ((LoggingManager) javac.getLogging()).addStandardErrorListener(listener);

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
        for (String line : errorStream.toString().split("\n")) {
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
                        .line(parseInt(matcher.group(2)))
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
    public RuntimeException handleInternalFailure(File reportDir, RuntimeException e) {
        return e;
    }
}
