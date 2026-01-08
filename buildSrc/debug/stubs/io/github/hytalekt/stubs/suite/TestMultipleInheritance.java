// Generated stub for testing - Parameter names should be preserved!
package io.github.hytalekt.stubs.suite;

import io.github.kytale.stubs.GeneratedStubException;
import java.io.IOException;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class TestMultipleInheritance implements TestInterface, TestInterfaceWithGenerics<String, Integer> {
  public static final String METHOD_CONSTANT = null;

  public TestMultipleInheritance() {
    throw new GeneratedStubException();
  }

  @Override
  public void basicMethod() {
    throw new GeneratedStubException();
  }

  @Override
  public String methodWithReturn() {
    throw new GeneratedStubException();
  }

  @Override
  public <T> T genericMethod(T value) {
    throw new GeneratedStubException();
  }

  @Override
  public String getValue(Integer key) {
    throw new GeneratedStubException();
  }

  @Override
  public <R> List<R> transform(String item) {
    throw new GeneratedStubException();
  }

  @Override
  public void setValue(String value) {
    throw new GeneratedStubException();
  }

  @Override
  public Integer getNumber() {
    throw new GeneratedStubException();
  }

  @Override
  public void methodWithThrows() throws IOException {
    throw new GeneratedStubException();
  }

  @Override
  public String defaultMethod() {
    throw new GeneratedStubException();
  }
}
