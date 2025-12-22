package io.github.hytalekt.stubs.suite;

import java.util.List;
import java.util.Map;

public class TestComplexGenerics<T extends Comparable<T>> {
    private Map<String, List<? extends T>> complexField;

    public <U, V extends U> Map<String, V> complexMethod(
        List<U> input
    ) {
        return null;
    }

    public void wildcardMethod(List<?> items) {
    }

    public <K extends Comparable<? super K>> void boundedWildcard(K key) {
    }
}