package io.github.hytalekt.stubs.suite;

import java.util.List;

public interface TestInterfaceWithGenerics<T, U extends Number> {
    T getValue(U key);

    <R> List<R> transform(T item);

    void setValue(T value);

    U getNumber();
}