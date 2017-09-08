package com.palantir.gradle.circlestyle;

import java.io.File;
import java.io.IOException;
import java.util.List;

interface FailuresSupplier {
    List<Failure> getFailures() throws IOException;

    RuntimeException handleInternalFailure(File reportDir, RuntimeException e);
}
