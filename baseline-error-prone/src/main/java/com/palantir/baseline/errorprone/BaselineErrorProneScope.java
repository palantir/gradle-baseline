/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.errorprone;

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.util.RuntimeVersion;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Scope.LookupKind;
import com.sun.tools.javac.code.Symbol;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * A compatibility wrapper around {@link com.sun.tools.javac.util.Filter}.
 * Adapted from {@link com.google.errorprone.util.ErrorProneScope} with additional methods.
 *
 * Original code from (Apache-2.0 License):
 * https://github.com/google/error-prone/blob/ce87ca1c7bb005371837b82ffa69041dd8a356e5/check_api/src/main/java/com/google/errorprone/util/ErrorProneScope.java
 *
 * TODO(fwindheuser): Delete after upstreaming missing methods into "ErrorProneScope".
 */
@SuppressWarnings("ThrowError")
public final class BaselineErrorProneScope {

    @SuppressWarnings("unchecked") // reflection
    public Iterable<Symbol> getSymbols(Predicate<Symbol> predicate, LookupKind lookupKind) {
        return (Iterable<Symbol>) invoke(getSymbols, maybeAsFilter(predicate), lookupKind);
    }

    private static final Class<?> FILTER_CLASS = getFilterClass();

    private static Class<?> getFilterClass() {
        if (RuntimeVersion.isAtLeast17()) {
            return null;
        }
        try {
            return Class.forName("com.sun.tools.javac.util.Filter");
        } catch (ClassNotFoundException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    private static final Method getSymbols = getImpl("getSymbols", Predicate.class, LookupKind.class);

    private static Method getImpl(String name, Class<?>... parameters) {
        return FILTER_CLASS != null
                ? getMethodOrDie(
                        Scope.class,
                        name,
                        Arrays.stream(parameters)
                                .map(p -> p.equals(Predicate.class) ? FILTER_CLASS : p)
                                .toArray(Class<?>[]::new))
                : getMethodOrDie(Scope.class, name, parameters);
    }

    private final Scope scope;

    BaselineErrorProneScope(Scope scope) {
        this.scope = scope;
    }

    private Object invoke(Method method, Object... args) {
        try {
            return method.invoke(scope, args);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    @SuppressWarnings("ProxyNonConstantType")
    private Object maybeAsFilter(Predicate<Symbol> predicate) {
        if (FILTER_CLASS == null) {
            return predicate;
        }
        return Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {FILTER_CLASS}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        checkState(method.getName().equals("accepts"));
                        return predicate.test((Symbol) args[0]);
                    }
                });
    }

    private static Method getMethodOrDie(Class<?> clazz, String name, Class<?>... parameters) {
        try {
            return clazz.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }
}
