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

package com.palantir.baseline.errorprone;

import org.junit.jupiter.api.Test;

class ImplicitPublicBuilderConstructorTest {

    @Test
    void fixSimpleCase() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {",
                        "    private Builder() {}",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void fixWithFields() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {",
                        "    private int foo;",
                        "    private String bar = \"bar\";",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {",
                        "    private int foo;",
                        "    private String bar = \"bar\";",
                        "    private Builder() {}",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void fixWithFieldsWithoutSpacing() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {private int foo;public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static Builder builder() {",
                        "    return new Builder();",
                        "  }",
                        "  public static class Builder {",
                        "    private int foo;",
                        "    private Builder() {}",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testNoStaticFactory() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static class Builder {",
                        "    public Test build() {",
                        "      return new Test();",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ImplicitPublicBuilderConstructor(), getClass());
    }
}
