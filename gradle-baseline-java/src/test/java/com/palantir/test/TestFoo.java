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

package com.palantir.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestFoo {
    @Test
    void is_debug() {
        System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .toString()
                .contains("-agentlib:jdwp"));
    }

    @Test
    void heh(@TempDir Path dir) throws IOException, InterruptedException {
        Files.writeString(dir.resolve("settings.gradle"), "");
        Files.writeString(dir.resolve("build.gradle"), "Thread.sleep(5_000);");

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                GradleRunner.create()
                        .withPluginClasspath()
                        .forwardOutput()
                        .withProjectDir(dir.toFile())
                        .build();
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES);
    }
}
