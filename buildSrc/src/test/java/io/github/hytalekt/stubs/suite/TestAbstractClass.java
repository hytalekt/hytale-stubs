package io.github.hytalekt.stubs.suite;

public abstract class TestAbstractClass {
    private String privateField;
    public String publicField;
    protected int protectedField;
    static String staticField;

    public TestAbstractClass(String value) {
        this.publicField = value;
    }

    public String getValue() {
        return publicField;
    }

    protected void protectedMethod() {
    }

    static void staticMethod() {
    }

    abstract String abstractMethod();
}