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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.cloud.dataflow.sdk.transforms.windowing.IntervalWindow;
import com.google.cloud.dataflow.sdk.util.state.State;
import com.google.cloud.dataflow.sdk.util.state.StateNamespace;
import com.google.cloud.dataflow.sdk.util.state.StateNamespaces;
import com.google.cloud.dataflow.sdk.util.state.StateTag;
import com.google.protobuf.ByteString;

import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Objects;

/**
 * Tests for {@link WindmillStateCache}.
 */
@RunWith(JUnit4.class)
public class WindmillStateCacheTest {
  private static final String COMPUTATION = "computation";
  private static final ByteString KEY = ByteString.copyFromUtf8("key");
  private static final String STATE_FAMILY = "family";

  private static class TestStateTag implements StateTag<TestState> {
    final String id;

    TestStateTag(String id) {
      this.id = id;
    }

    @Override
    public void appendTo(Appendable appendable) throws IOException {
      appendable.append(id);
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public TestState bind(StateBinder binder) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Tag(" + id + ")";
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof TestStateTag) && Objects.equals(((TestStateTag) other).id, id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  private static class TestState implements State {
    String value = null;

    TestState(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    @Override
    public void clear() {
      this.value = null;
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof TestState) && Objects.equals(((TestState) other).value, value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return "State(" + value + ")";
    }
  }

  private static StateNamespace windowNamespace(long start) {
    return StateNamespaces.window(
        IntervalWindow.getCoder(), new IntervalWindow(new Instant(start), new Instant(start + 1)));
  }

  private static StateNamespace triggerNamespace(long start, int triggerIdx) {
    return StateNamespaces.windowAndTrigger(IntervalWindow.getCoder(),
        new IntervalWindow(new Instant(start), new Instant(start + 1)), triggerIdx);
  }

  WindmillStateCache cache;
  WindmillStateCache.ForKey keyCache;

  @Before
  public void setUp() {
    cache = new WindmillStateCache();
    keyCache = cache.forComputation(COMPUTATION).forKey(KEY, STATE_FAMILY, 0L);
    assertEquals(0, cache.getWeight());
  }

  @Test
  public void testBasic() throws Exception {
    assertNull(keyCache.get(StateNamespaces.global(), new TestStateTag("tag1")));
    assertNull(keyCache.get(windowNamespace(0), new TestStateTag("tag2")));
    assertNull(keyCache.get(triggerNamespace(0, 0), new TestStateTag("tag3")));
    assertNull(keyCache.get(triggerNamespace(0, 0), new TestStateTag("tag2")));

    keyCache.put(StateNamespaces.global(), new TestStateTag("tag1"), new TestState("g1"), 2);
    assertEquals(5, cache.getWeight());
    keyCache.put(windowNamespace(0), new TestStateTag("tag2"), new TestState("w2"), 2);
    assertEquals(10, cache.getWeight());
    keyCache.put(triggerNamespace(0, 0), new TestStateTag("tag3"), new TestState("t3"), 2);
    assertEquals(12, cache.getWeight());
    keyCache.put(triggerNamespace(0, 0), new TestStateTag("tag2"), new TestState("t2"), 2);
    assertEquals(14, cache.getWeight());

    assertEquals(
        new TestState("g1"), keyCache.get(StateNamespaces.global(), new TestStateTag("tag1")));
    assertEquals(new TestState("w2"), keyCache.get(windowNamespace(0), new TestStateTag("tag2")));
    assertEquals(
        new TestState("t3"), keyCache.get(triggerNamespace(0, 0), new TestStateTag("tag3")));
    assertEquals(
        new TestState("t2"), keyCache.get(triggerNamespace(0, 0), new TestStateTag("tag2")));
  }

  /**
   * Verifies that values are cached in the appropriate namespaces.
   */
  @Test
  public void testInvalidation() throws Exception {
    assertNull(keyCache.get(StateNamespaces.global(), new TestStateTag("tag1")));
    keyCache.put(StateNamespaces.global(), new TestStateTag("tag1"), new TestState("g1"), 2);
    assertEquals(5, cache.getWeight());
    assertEquals(
        new TestState("g1"), keyCache.get(StateNamespaces.global(), new TestStateTag("tag1")));

    keyCache = cache.forComputation(COMPUTATION).forKey(KEY, STATE_FAMILY, 1L);
    assertEquals(5, cache.getWeight());
    assertNull(keyCache.get(StateNamespaces.global(), new TestStateTag("tag1")));
    assertEquals(0, cache.getWeight());
  }

  /**
   * Verifies that the cache is invalidated when the cache token changes.
   */
  @Test
  public void testEviction() throws Exception {
    keyCache.put(windowNamespace(0), new TestStateTag("tag2"), new TestState("w2"), 2);
    assertEquals(5, cache.getWeight());
    keyCache.put(triggerNamespace(0, 0), new TestStateTag("tag3"), new TestState("t3"), 2000000000);
    assertEquals(0, cache.getWeight());
    // Eviction is atomic across the whole window.
    assertNull(keyCache.get(windowNamespace(0), new TestStateTag("tag2")));
    assertNull(keyCache.get(triggerNamespace(0, 0), new TestStateTag("tag3")));
  }

  /**
   * Verifies that caches are kept independently per-key.
   */
  @Test
  public void testMultipleKeys() throws Exception {
    WindmillStateCache.ForKey keyCache1 = cache.forComputation("comp1").forKey(
        ByteString.copyFromUtf8("key1"), STATE_FAMILY, 0L);
    WindmillStateCache.ForKey keyCache2 = cache.forComputation("comp1").forKey(
        ByteString.copyFromUtf8("key2"), STATE_FAMILY, 0L);
    WindmillStateCache.ForKey keyCache3 = cache.forComputation("comp2").forKey(
        ByteString.copyFromUtf8("key1"), STATE_FAMILY, 0L);

    keyCache1.put(StateNamespaces.global(), new TestStateTag("tag1"), new TestState("g1"), 2);
    assertEquals(
        new TestState("g1"), keyCache1.get(StateNamespaces.global(), new TestStateTag("tag1")));
    assertNull(keyCache2.get(StateNamespaces.global(), new TestStateTag("tag1")));
    assertNull(keyCache3.get(StateNamespaces.global(), new TestStateTag("tag1")));
  }
}
