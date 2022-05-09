package com.palantir.baseline.plugins.javaversions;

// CHECKSTYLE:OFF

import javax.inject.Inject;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.process.internal.ExecActionFactory;
// CHECKSTYLE:ON

final class BaselineJavadocToolAdapter extends JavadocToolAdapter {
    private final BaselineJavadocTool javadocTool;

    BaselineJavadocToolAdapter(ExecActionFactory execActionFactory, BaselineJavadocTool javadocTool) {
        super(execActionFactory, null);
        this.javadocTool = javadocTool;
    }

    @Override
    public JavaInstallationMetadata getMetadata() {
        return javadocTool.getMetadata();
    }

    @Override
    public RegularFile getExecutablePath() {
        return javadocTool.getExecutablePath();
    }

    public static BaselineJavadocToolAdapter create(ObjectFactory objectFactory, BaselineJavadocTool javadocTool) {
        return new BaselineJavadocToolAdapter(
                objectFactory.newInstance(ExecActionFactoryGrabber.class).getExecActionFactory(), javadocTool);
    }

    interface ExecActionFactoryGrabber {
        @Inject
        ExecActionFactory getExecActionFactory();
    }
}
