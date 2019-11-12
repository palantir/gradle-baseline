package com.palantir.baseline.refaster;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.lang.reflect.InvocationTargetException;

/**
 * {@link InvocationTargetException#getTargetException()} javadoc recommends using
 * {@link InvocationTargetException#getCause()} instead. getTargetException was added
 * before throwables had getCause.
 */
public final class InvocationTargetExceptionCause {

    @BeforeTemplate
    Throwable before(InvocationTargetException ite) {
        return ite.getTargetException();
    }

    @AfterTemplate
    Throwable after(InvocationTargetException ite) {
        return ite.getCause();
    }
}
