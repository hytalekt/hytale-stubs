package io.github.hytalekt.stubs.suite;

@Deprecated(since = "1.5", forRemoval = true)
@SuppressWarnings("all")
public class TestAnnotatedClass {
    @Deprecated
    public String oldField;

    @Override
    public String toString() {
        return super.toString();
    }

    @Deprecated(forRemoval = true)
    public void oldMethod() {
    }
}