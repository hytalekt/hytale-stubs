package io.github.hytalekt.stubs.suite;

public class TestInnerClasses {
    private String outerField;

    public class InnerClass {
        private String innerField;

        public InnerClass() {
        }

        public String getInnerValue() {
            return innerField;
        }

        public String getOuterValue() {
            return outerField;
        }
    }

    public static class StaticInnerClass {
        private int value;

        public StaticInnerClass(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    protected class ProtectedInnerClass {
        protected void protectedMethod() {
        }
    }

    public String getOuter() {
        return outerField;
    }
}