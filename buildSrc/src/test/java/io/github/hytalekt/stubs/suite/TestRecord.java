package io.github.hytalekt.stubs.suite;

public record TestRecord(String name, int age, double salary) {
    public TestRecord {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
    }

    public String getInfo() {
        return name + ": " + age;
    }
}