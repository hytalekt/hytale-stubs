package io.github.hytalekt.stubs.suite;

public class TestExtendingClass extends TestAbstractClass {
    private String extendedField;

    public TestExtendingClass() {
        super("extending");
    }

    @Override
    public String getValue() {
        return super.getValue();
    }

    public String getExtendedValue() {
        return extendedField;
    }

    @Override
    public String abstractMethod() {
        return "implemented";
    }
}