/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.testcompilerplugins;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;
import java.lang.management.ManagementFactory;

@AutoService(Plugin.class)
public final class LogCompilerInfoPlugin implements Plugin {
    @Override
    public String getName() {
        return "LogCompilerInfo";
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void init(JavacTask task, String... _args) {
        System.out.println("Compiler Java Version: " + System.getProperty("java.specification.version"));
        System.out.println("Compiler Java Home: " + System.getProperty("java.home"));

        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(inputArgument -> {
            System.out.println("Compiler Process Arg: " + inputArgument);
        });

        Context context = ((BasicJavacTask) task).getContext();
        Options options = Options.instance(context);
        options.keySet().forEach(optionKey -> {
            System.out.printf("Compiler Arg: %s=%s%n", optionKey, options.get(optionKey));
        });
    }
}
