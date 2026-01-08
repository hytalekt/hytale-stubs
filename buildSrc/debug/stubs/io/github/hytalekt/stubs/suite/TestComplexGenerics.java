// Generated stub for testing - Parameter names should be preserved!
package io.github.hytalekt.stubs.suite;

import io.github.kytale.stubs.GeneratedStubException;
import java.lang.Comparable;
import java.lang.String;
import java.lang.Void;
import java.util.List;
import java.util.Map;

public class TestComplexGenerics<T extends Comparable<T>> {
  public TestComplexGenerics() {
    throw new GeneratedStubException();
  }

  public <U, V extends U> Map<String, Void> complexMethod(List<U> input) {
    throw new GeneratedStubException();
  }

  public void wildcardMethod(List<?> items) {
    throw new GeneratedStubException();
  }

  public <K extends Comparable<? super K>> void boundedWildcard(K key) {
    throw new GeneratedStubException();
  }
}
