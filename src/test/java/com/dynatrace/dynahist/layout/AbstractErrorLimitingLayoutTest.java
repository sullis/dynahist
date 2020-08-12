/*
 * Copyright 2020 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dynatrace.dynahist.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public abstract class AbstractErrorLimitingLayoutTest {

  protected abstract AbstractLayout createLayout(
      final double absoluteError,
      final double relativeError,
      final double minValue,
      final double maxValue);

  protected final void assertIndexSymmetry(int idx, int negativeIdx) {
    assertEquals(-idx - 1, negativeIdx);
  }

  @Test
  public void test1() {
    Layout layout = createLayout(1e-6, 0.001, 0, 1);
    assertTrue(layout.getUnderflowBinIndex() >= layout.mapToBinIndex(Double.NEGATIVE_INFINITY));
    assertTrue(layout.getOverflowBinIndex() <= layout.mapToBinIndex(Double.POSITIVE_INFINITY));
    assertEquals(
        Double.NEGATIVE_INFINITY, layout.getBinLowerBound(layout.getUnderflowBinIndex()), 0d);
    assertEquals(
        Double.POSITIVE_INFINITY, layout.getBinUpperBound(layout.getOverflowBinIndex()), 0d);
  }

  @Test
  public void testGeneral() {
    final double[] absoluteErrors = {1e0, 1e1, 1e2, 1e3};
    final double[] relativeErrors = {1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3};

    final double minValue = -1e6;
    final double maxValue = 1e6;

    final int numValues = 1_000;

    final double eps = 1e-6;

    final double[] values = new double[numValues];

    final Random random = new Random(0);
    for (int i = 0; i < numValues; ++i) {
      values[i] = minValue + random.nextDouble() * (maxValue - minValue);
    }

    for (final double absoluteError : absoluteErrors) {
      for (final double relativeError : relativeErrors) {

        final Layout layout = createLayout(absoluteError, relativeError, minValue, maxValue);

        LayoutTestUtil.assertConsistency(layout);

        for (final double value : values) {

          final int idx = layout.mapToBinIndex(value);
          final int negativeIdx = layout.mapToBinIndex(-value);

          assertIndexSymmetry(idx, negativeIdx);

          final double lowerBound = layout.getBinLowerBound(idx);
          final double upperBound = layout.getBinUpperBound(idx);

          assertThat(lowerBound).isLessThanOrEqualTo(value);
          assertThat(upperBound).isGreaterThanOrEqualTo(value);
          final boolean isRelativeErrorLimitFulfilled =
              Math.abs(upperBound - lowerBound)
                      / Math.max(Math.abs(lowerBound), Math.abs(upperBound))
                  <= relativeError * (1. + eps);
          final boolean isAbsoluteErrorLimitFulfilled =
              Math.abs(upperBound - lowerBound) <= absoluteError * (1. + eps);

          assertTrue(isAbsoluteErrorLimitFulfilled || isRelativeErrorLimitFulfilled);
        }

        for (int i = layout.getUnderflowBinIndex() + 1;
            i <= layout.getOverflowBinIndex() - 1;
            ++i) {

          final double lowerBound = layout.getBinLowerBound(i);
          final double upperBound = layout.getBinUpperBound(i);
          final boolean isRelativeErrorLimitFulfilled =
              Math.abs(upperBound - lowerBound)
                      / Math.max(Math.abs(lowerBound), Math.abs(upperBound))
                  <= relativeError * (1. + eps);
          final boolean isAbsoluteErrorLimitFulfilled =
              Math.abs(upperBound - lowerBound) <= absoluteError * (1. + eps);
          assertTrue(isAbsoluteErrorLimitFulfilled || isRelativeErrorLimitFulfilled);
        }
      }
    }
  }

  @Test
  public void testLargeLayout() {
    Layout layout = createLayout(1e-6, 1e-3, -1e12, 1e12);

    for (int binIndex = layout.getUnderflowBinIndex() + 1;
        binIndex < layout.getOverflowBinIndex();
        ++binIndex) {
      assertEquals(binIndex, layout.mapToBinIndex(layout.getBinLowerBound(binIndex)));
      assertEquals(binIndex, layout.mapToBinIndex(layout.getBinUpperBound(binIndex)));
    }
  }

  @Test
  public void testGetBinLowerBoundApproximation() {
    double absoluteError = 1;
    double relativeError = 0.01;
    double eps = 1e-4;

    AbstractLayout layout = createLayout(absoluteError, relativeError, 0, 2000);
    for (int transitionIdx = 0; transitionIdx <= layout.getOverflowBinIndex(); ++transitionIdx) {
      double transition = layout.getBinLowerBoundApproximation(transitionIdx);
      double transitionLow =
          Math.min(transition * (1 - eps * relativeError), transition - eps * absoluteError);
      double transitionHigh =
          Math.max(transition * (1 + eps * relativeError), transition + eps * absoluteError);
      int binIndexLow = transitionIdx - 1;
      int binIndexHigh = transitionIdx;
      assertEquals(binIndexLow, layout.mapToBinIndex(transitionLow));
      assertEquals(binIndexHigh, layout.mapToBinIndex(transitionHigh));
    }
  }

  @Test
  public void testCreate() {
    assertThrows(
        IllegalArgumentException.class,
        () -> createLayout(1e-8, 1e-2, -1e6, Double.POSITIVE_INFINITY));
    assertThrows(
        IllegalArgumentException.class,
        () -> createLayout(1e-8, 1e-2, Double.NEGATIVE_INFINITY, 1e6));
    assertThrows(IllegalArgumentException.class, () -> createLayout(1e-8, 1e-2, 1e6, 1e-6));
    assertThrows(IllegalArgumentException.class, () -> createLayout(1e-8, 1e-2, 1, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> createLayout(1e-8, 1e-2, Double.NaN, 1));

    assertThrows(
        IllegalArgumentException.class, () -> ErrorLimitingLayout1.create(1e-8, 1e-10, 1e-6, 1e6));
    assertThrows(
        IllegalArgumentException.class,
        () -> ErrorLimitingLayout1.create(1e-8, 1e-10, Long.MIN_VALUE, 1e6));
    assertThrows(
        IllegalArgumentException.class, () -> ErrorLimitingLayout1.create(1e-8, 1e-9, 1e-6, 1e6));
  }

  /*@Test
  public void layoutCreateBug(){
    Layout layout =  LayoutApproxOrder1.create(1e-8, 1e-9, 1e-6, 1e6);//TODO Bug overflowBinIndex < underflowBinIndex (int overflow)
  }*/
}
