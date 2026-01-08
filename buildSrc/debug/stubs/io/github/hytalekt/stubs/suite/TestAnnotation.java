// Generated stub for testing - Parameter names should be preserved!
package io.github.hytalekt.stubs.suite;

import java.lang.Class;
import java.lang.Object;
import java.lang.String;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public abstract @interface TestAnnotation {
  String value() default "";

  int priority() default 0;

  Class<?> type() default Object.class;
}
