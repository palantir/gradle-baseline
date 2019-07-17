package com.palantir.baseline.refaster;

import org.junit.Test;

public class CollectionsIsEmptyTest {

    @Test
    public void isEmpty() {
        RefasterTestHelper
                .forRefactoring(CollectionsIsEmpty.class)
                .withInputLines(
                        "Test",
                        "import java.util.ArrayList;",
                        "public class Test {",
                        "  boolean empty = new ArrayList<>().size() == 0;",
                        "}")
                .hasOutputLines(
                        "import java.util.ArrayList;",
                        "public class Test {",
                        "  boolean empty = new ArrayList<>().isEmpty();",
                        "}");
    }

}
