package com.palantir.baseline.refaster;

import org.junit.Test;

public class SortedFirstTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(SortedFirst.class)
                .withInputLines(
                        "Test",
                        "import java.util.*;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream()",
                        "      .sorted(Comparator.reverseOrder())",
                        "      .findFirst();",
                        "}")
                .hasOutputLines(
                        "import java.util.*;",
                        "import java.util.stream.Stream;",
                        "public class Test {",
                        "  Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream()"
                                + ".min(Comparator.reverseOrder());",
                        "}");
    }

}
