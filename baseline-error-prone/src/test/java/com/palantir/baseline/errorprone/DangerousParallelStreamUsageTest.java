/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class DangerousParallelStreamUsageTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousParallelStreamUsage.class, getClass());
    }

    @Test
    public void should_warn_when_parallel_with_no_arguments_is_invoked_on_subclass_of_java_stream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.ArrayList;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<String> list = new ArrayList<>();",
                        "       // BUG: Diagnostic contains: Should not use parallel Java streams.",
                        "       list.stream().parallel();",
                        "   }",
                        "}")
                .doTest();
    }

    @SuppressWarnings("MethodLength")
    @Test
    public void should_also_warn_when_parallel_with_arguments_is_invoked_on_subclass_of_java_stream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Comparator;",
                        "import java.util.Iterator;",
                        "import java.util.Optional;",
                        "import java.util.Spliterator;",
                        "import java.util.concurrent.ForkJoinPool;",
                        "import java.util.function.BiConsumer;",
                        "import java.util.function.BiFunction;",
                        "import java.util.function.BinaryOperator;",
                        "import java.util.function.Consumer;",
                        "import java.util.function.Function;",
                        "import java.util.function.IntFunction;",
                        "import java.util.function.Predicate;",
                        "import java.util.function.Supplier;",
                        "import java.util.function.ToDoubleFunction;",
                        "import java.util.function.ToIntFunction;",
                        "import java.util.function.ToLongFunction;",
                        "import java.util.stream.Collector;",
                        "import java.util.stream.DoubleStream;",
                        "import java.util.stream.IntStream;",
                        "import java.util.stream.LongStream;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "   public static class FooStream<T> implements Stream<T> {",
                        "       @Override",
                        "       public Stream<T> filter(Predicate<? super T> predicate) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public IntStream mapToInt(ToIntFunction<? super T> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public LongStream mapToLong(ToLongFunction<? super T> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>>"
                                + " mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream>"
                                + " mapper) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> distinct() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> sorted() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> sorted(Comparator<? super T> comparator) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> peek(Consumer<? super T> action) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> limit(long maxSize) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> skip(long n) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public void forEach(Consumer<? super T> action) {",
                        "       }",
                        "       @Override",
                        "       public void forEachOrdered(Consumer<? super T> action) {",
                        "       }",
                        "       @Override",
                        "       public Object[] toArray() {",
                        "           return new Object[0];",
                        "       }",
                        "       @Override",
                        "       public <A> A[] toArray(IntFunction<A[]> generator) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public T reduce(T identity, BinaryOperator<T> accumulator) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Optional<T> reduce(BinaryOperator<T> accumulator) {",
                        "           return Optional.empty();",
                        "       }",
                        "       @Override",
                        "       public <U> U reduce(",
                        "               U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U>"
                                + " combiner) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public <R> R collect(",
                        "               Supplier<R> supplier, ",
                        "               BiConsumer<R, ? super T> accumulator, ",
                        "               BiConsumer<R, R> combiner",
                        "       ) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public <R, A> R collect(Collector<? super T, A, R> collector) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Optional<T> min(Comparator<? super T> comparator) {",
                        "           return Optional.empty();",
                        "       }",
                        "       @Override",
                        "       public Optional<T> max(Comparator<? super T> comparator) {",
                        "           return Optional.empty();",
                        "       }",
                        "       @Override",
                        "       public long count() {",
                        "           return 0;",
                        "       }",
                        "       @Override",
                        "       public boolean anyMatch(Predicate<? super T> predicate) {",
                        "           return false;",
                        "       }",
                        "       @Override",
                        "       public boolean allMatch(Predicate<? super T> predicate) {",
                        "           return false;",
                        "       }",
                        "       @Override",
                        "       public boolean noneMatch(Predicate<? super T> predicate) {",
                        "           return false;",
                        "       }",
                        "       @Override",
                        "       public Optional<T> findFirst() {",
                        "           return Optional.empty();",
                        "       }",
                        "       @Override",
                        "       public Optional<T> findAny() {",
                        "           return Optional.empty();",
                        "       }",
                        "       @Override",
                        "       public Iterator<T> iterator() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Spliterator<T> spliterator() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public boolean isParallel() {",
                        "           return false;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> sequential() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> parallel() {",
                        "           return null;",
                        "       }",
                        "       public Stream<T> parallel(ForkJoinPool yourPool) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> unordered() {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public Stream<T> onClose(Runnable closeHandler) {",
                        "           return null;",
                        "       }",
                        "       @Override",
                        "       public void close() {",
                        "       }",
                        "   }",
                        "   public static final ForkJoinPool POOL_FOR_THIS_CLASS = new ForkJoinPool();",
                        "   public static final void main(String[] args) {",
                        "       FooStream<String> fooStream = new FooStream<>();",
                        "       // BUG: Diagnostic contains: Should not use parallel Java streams.",
                        "       fooStream.parallel();",
                        "       // This should fail too because it still won't allow you to properly control"
                                + " parallelism",
                        "       // BUG: Diagnostic contains: Should not use parallel Java streams.",
                        "       fooStream.parallel(POOL_FOR_THIS_CLASS);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_warn_collection_parallelStream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.ArrayList;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       List<String> list = new ArrayList<>();",
                        "       // BUG: Diagnostic contains: Should not use parallel Java streams.",
                        "       list.parallelStream();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_warn_stream_support_parallel() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.stream.StreamSupport;",
                        "class Test {",
                        "   public static final void main(String[] args) {",
                        "       StreamSupport.stream(new ArrayList<>().spliterator(), false);",
                        "       StreamSupport.stream(new ArrayList<>().spliterator(), args.length == 4);",
                        "       // BUG: Diagnostic contains: Should not use parallel Java streams.",
                        "       StreamSupport.stream(new ArrayList<>().spliterator(), true);",
                        "   }",
                        "}")
                .doTest();
    }
}
