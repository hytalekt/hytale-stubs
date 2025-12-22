package io.github.hytalekt.stubs.suite;

import java.io.IOException;
import java.util.List;

public class TestMethodVariations {
    public void basicMethod() {
    }

    public String methodWithReturn() {
        return "test";
    }

    public void methodWithThrows() throws IOException, IllegalArgumentException {
    }

    public void methodWithParams(String name, int value, double decimal) {
    }

    public <T> List<T> genericMethodWithParams(T item, int count) {
        return null;
    }

    public synchronized void synchronizedMethod() {
    }

    public static void staticMethod() {
    }

    public final void finalMethod() {
    }

    protected void protectedMethod() {
    }

    public void methodWithMultipleThrows()
        throws IOException, RuntimeException, IllegalStateException {
    }
}