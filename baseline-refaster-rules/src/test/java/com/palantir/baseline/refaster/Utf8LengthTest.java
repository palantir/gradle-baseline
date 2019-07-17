package com.palantir.baseline.refaster;

import org.junit.Test;

public class Utf8LengthTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(Utf8Length.class)
                .withInputLines(
                        "Test",
                         "import java.nio.charset.StandardCharsets;",
                        "public class Test {",
                        "  int i = \"hello world\".getBytes(StandardCharsets.UTF_8).length;",
                        "}")
                .hasOutputLines(
                        "import com.google.common.base.Utf8;",
                        "import java.nio.charset.StandardCharsets;",
                        "public class Test {",
                        "  int i = Utf8.encodedLength(\"hello world\");",
                        "}");
    }

}
