/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.util;

import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.transforms.windowing.BoundedWindow;
import com.google.cloud.dataflow.sdk.transforms.windowing.PaneInfo;
import com.google.cloud.dataflow.sdk.util.state.StateInternals;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.dataflow.sdk.values.TupleTag;

import org.joda.time.Instant;

import java.io.IOException;
import java.util.Collection;

/**
 * Interface that may be required by some (internal) {@code DoFn}s to implement windowing. It should
 * not be necessary for general user code to interact with this at all.
 *
 * <p>This interface should be provided by runner implementors to support windowing on their runner.
 *
 * @param <InputT> input type
 * @param <OutputT> output type
 */
public interface WindowingInternals<InputT, OutputT> {

  StateInternals stateInternals();

  /**
   * Output the value at the specified timestamp in the listed windows.
   */
  void outputWindowedValue(OutputT output, Instant timestamp,
      Collection<? extends BoundedWindow> windows, PaneInfo pane);

  /**
   * Return the timer manager provided by the underlying system, or null if Timers need
   * to be emulated.
   */
  TimerInternals timerInternals();

  /**
   * Access the windows the element is being processed in without "exploding" it.
   */
  Collection<? extends BoundedWindow> windows();

  /**
   * Access the pane of the current window(s).
   */
  PaneInfo pane();

  /**
   * Write the given {@link PCollectionView} data to a location accessible by other workers.
   */
  <T> void writePCollectionViewData(
      TupleTag<?> tag,
      Iterable<WindowedValue<T>> data,
      Coder<T> elemCoder) throws IOException;
}
