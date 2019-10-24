package test;

public class Test {
    void test() {
        int x = 1;
        System.out.println(
                "Hello");
        Optional.of("hello").orElseGet(() -> {
            return "Hello World";
        });
    }
}
