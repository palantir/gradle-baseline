class StringReflowing {
    byte[] bytes = ("one long incredibly unbroken sentence moving from topic to topic so that no-one had a chance to"
                    + " interrupt at all")
            .getBytes();

    String foo = callRandomMethod(
            "one long incredibly unbroken sentence moving from topic to topic so that no-one had a chance to interrupt"
                    + " at all");
}
