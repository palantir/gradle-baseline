/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
