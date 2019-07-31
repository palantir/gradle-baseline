/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.refaster;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.List;

/**
 * A compiler plugin based on {@link com.google.errorprone.refaster.RefasterRuleCompiler} that collapses multiple
 * source files into a single refaster {@link com.google.errorprone.CodeTransformer}.
 */
@AutoService(Plugin.class)
public final class BaselineRefasterCompiler implements Plugin {
    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public void init(JavacTask task, String... args) {
        List<String> listArgs = Arrays.asList(args);
        int outIndex = listArgs.indexOf("--out");
        com.palantir.logsafe.Preconditions.checkArgument(outIndex != -1, "No --out specified");
        com.palantir.logsafe.Preconditions.checkArgument(listArgs.size() > outIndex, "No value passed for --out");
        String path = listArgs.get(outIndex + 1);

        com.palantir.logsafe.Preconditions.checkArgument(task instanceof BasicJavacTask, "JavacTask not instance of BasicJavacTask");
        task.addTaskListener(new BaselineRefasterCompilerAnalyzer(
                ((BasicJavacTask) task).getContext(), FileSystems.getDefault().getPath(path)));
    }
}
