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

package com.google.cloud.dataflow.sdk.runners.worker;

import static com.google.api.client.util.Base64.decodeBase64;
import static com.google.cloud.dataflow.sdk.util.Structs.getString;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.util.CloudObject;
import com.google.cloud.dataflow.sdk.util.ExecutionContext;
import com.google.cloud.dataflow.sdk.util.PropertyNames;
import com.google.cloud.dataflow.sdk.util.WindowedValue;
import com.google.cloud.dataflow.sdk.util.common.CounterSet;
import com.google.cloud.dataflow.sdk.util.common.worker.NativeReader;
import com.google.cloud.dataflow.sdk.values.KV;

import javax.annotation.Nullable;

/**
 * Creates a PartitioningShuffleReader from a CloudObject spec.
 */
public class PartitioningShuffleReaderFactory implements ReaderFactory {

  @Override
  public NativeReader<?> create(
      CloudObject spec,
      @Nullable Coder<?> coder,
      @Nullable PipelineOptions options,
      @Nullable ExecutionContext executionContext,
      @Nullable CounterSet.AddCounterMutator addCounterMutator,
      @Nullable String operationName)
          throws Exception {
    checkNotNull(options, "PipelineOptions must not be null in PartitioningShuffleReaderFactory");
    @SuppressWarnings({"unchecked", "rawtypes"})
    Coder<WindowedValue<KV<Object, Object>>> typedCoder =
        (Coder<WindowedValue<KV<Object, Object>>>) coder;
    return createTyped(spec, options, typedCoder);
  }

  public <K, V> PartitioningShuffleReader<K, V> createTyped(
      CloudObject spec,
      PipelineOptions options,
      Coder<WindowedValue<KV<K, V>>> coder)
      throws Exception {
    return new PartitioningShuffleReader<K, V>(
        options,
        decodeBase64(getString(spec, PropertyNames.SHUFFLE_READER_CONFIG)),
        getString(spec, PropertyNames.START_SHUFFLE_POSITION, null),
        getString(spec, PropertyNames.END_SHUFFLE_POSITION, null),
        coder);
  }
}
