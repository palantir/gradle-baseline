package com.palantir.baseline.refaster;

import org.junit.Rule;
import org.junit.Test;

public class CollectionsIsEmptyTest {

    @Rule
    public RefasterRule refasterRule = new RefasterRule(CollectionsIsEmpty.class);

    @Test
    public void isEmpty() {
        refasterRule
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
