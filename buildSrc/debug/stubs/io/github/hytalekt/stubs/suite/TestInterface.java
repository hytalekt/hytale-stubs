// Generated stub for testing - Parameter names should be preserved!
package io.github.hytalekt.stubs.suite;

import io.github.kytale.stubs.GeneratedStubException;
import java.io.IOException;
import java.lang.String;

public interface TestInterface {
  String METHOD_CONSTANT = "constant";

  void basicMethod();

  String methodWithReturn();

  void methodWithThrows() throws IOException;

  <T> T genericMethod(T param0);

  default String defaultMethod() {
    throw new GeneratedStubException();
  }
}
