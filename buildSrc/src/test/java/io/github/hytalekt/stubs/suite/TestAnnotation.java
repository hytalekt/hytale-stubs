package io.github.hytalekt.stubs.suite;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TestAnnotation {
    String value() default "";
    int priority() default 0;
    Class<?> type() default Object.class;
}