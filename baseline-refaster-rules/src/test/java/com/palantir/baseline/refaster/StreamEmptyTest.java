package com.palantir.baseline.refaster;

import org.junit.Test;

public class StreamEmptyTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(StreamEmpty.class)
                .withInputLines(
                        "Test",
                        "import java.util.*;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Stream<Integer> i = Stream.of();",
                        "}")
                .hasOutputLines(
                        "import java.util.*;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Stream<Integer> i = Stream.empty();",
                        "}");
    }
}
