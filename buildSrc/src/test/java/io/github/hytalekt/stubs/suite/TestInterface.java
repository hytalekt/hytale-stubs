package io.github.hytalekt.stubs.suite;

import java.io.IOException;

public interface TestInterface {
    String METHOD_CONSTANT = "constant";

    void basicMethod();

    String methodWithReturn();

    void methodWithThrows() throws IOException;

    <T> T genericMethod(T value);

    default String defaultMethod() {
        return "default";
    }
}