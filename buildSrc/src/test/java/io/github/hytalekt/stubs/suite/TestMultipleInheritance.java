package io.github.hytalekt.stubs.suite;

public class TestMultipleInheritance
    implements TestInterface, TestInterfaceWithGenerics<String, Integer> {
    @Override
    public void basicMethod() {
    }

    @Override
    public String methodWithReturn() {
        return "implemented";
    }

    @Override
    public <T> T genericMethod(T value) {
        return value;
    }

    @Override
    public String getValue(Integer key) {
        return "value";
    }

    @Override
    public <R> java.util.List<R> transform(String item) {
        return null;
    }

    @Override
    public void setValue(String value) {
    }

    @Override
    public Integer getNumber() {
        return 0;
    }

    @Override
    public void methodWithThrows() throws java.io.IOException {
    }
}