package com.palantir.baseline.refaster;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.AlsoNegation;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.util.Collection;
import java.util.stream.Stream;

public class StreamEmpty<T> {

    @BeforeTemplate
    Stream<T> streamNoParams() {
        return Stream.of();
    }

    @AfterTemplate
    Stream<T> streamEmpty() {
        return Stream.empty();
    }

}
