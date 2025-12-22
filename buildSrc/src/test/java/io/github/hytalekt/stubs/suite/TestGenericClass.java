package io.github.hytalekt.stubs.suite;

import java.util.List;
import java.util.Map;

public class TestGenericClass<T, U extends Number> {
    private T value;
    private List<U> numbers;
    private Map<String, T> cache;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public <R> R transform(T input) {
        return null;
    }

    public <K extends Comparable<K>> K findMax(List<K> items) {
        return null;
    }

    public List<U> getNumbers() {
        return numbers;
    }
}