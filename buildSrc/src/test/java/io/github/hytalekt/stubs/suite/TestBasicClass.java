package io.github.hytalekt.stubs.suite;

public class TestBasicClass {
    private String privateField;
    public String publicField;
    protected int protectedField;
    static String staticField;

    public TestBasicClass() {
    }

    public TestBasicClass(String value) {
        this.publicField = value;
    }

    public String getValue() {
        return publicField;
    }

    protected void protectedMethod() {
    }

    static void staticMethod() {
    }
}