// Generated stub for testing - Parameter names should be preserved!
package io.github.hytalekt.stubs.suite;

import java.lang.Number;
import java.util.List;

public interface TestInterfaceWithGenerics<T, U extends Number> {
  T getValue(U param0);

  <R> List<R> transform(T param0);

  void setValue(T param0);

  U getNumber();
}
