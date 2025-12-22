package io.github.hytalekt.stubs.suite;

public enum TestEnum {
    VALUE_ONE("one"),
    VALUE_TWO("two"),
    VALUE_THREE("three");

    private final String description;

    TestEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}