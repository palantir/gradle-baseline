package com.palantir.baseline.refaster;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Repeated;
import org.assertj.core.api.Descriptable;

public final class AssertjDescribedAsFormat<T extends Descriptable<T>> {

    @BeforeTemplate
    public T before(T assertion, String format, @Repeated Object formatArgs) {
        return assertion.describedAs(String.format(format, formatArgs));
    }

    @AfterTemplate
    public T after(T assertion, String format, @Repeated Object formatArgs) {
        return assertion.describedAs(format, formatArgs);
    }
}
