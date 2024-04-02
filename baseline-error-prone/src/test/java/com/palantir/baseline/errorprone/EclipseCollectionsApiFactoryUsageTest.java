/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import org.junit.jupiter.api.Test;

public class EclipseCollectionsApiFactoryUsageTest {
    @Test
    public void desired_import_remains_unchanged() {
        fix().addInputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import org.eclipse.collections.impl.factory.primitive.LongLists;",
                        "public final class Client {",
                        "  public int getOrder() {",
                        "    return 66;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void bad_import_changes() {
        fix().addInputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import org.eclipse.collections.api.factory.primitive.LongLists;",
                        "public final class Client {",
                        "  public int getOrder() {",
                        "    return 66;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Client.java",
                        "package com.google.frobber;",
                        "import org.eclipse.collections.impl.factory.primitive.LongLists;",
                        "public final class Client {",
                        "  public int getOrder() {",
                        "    return 66;",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(EclipseCollectionsApiFactoryUsage.class, getClass());
    }
}
