/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.io.filesystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.watchman.Capability;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.EnumSet;
import org.junit.Test;

public class ExactPathMatcherTest {

  @Test
  public void matchesExplicitlyProvidedPaths() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertTrue(matcher.matches(Paths.get(".idea")));
  }

  @Test
  public void doesNotMatchPathsThatAreNotExactlyTheSame() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertFalse(matcher.matches(Paths.get(".ideas")));
  }

  @Test
  public void usesWatchmanQueryToMatchPathsExactlyMatchingProvidedOne() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertEquals(
        matcher.toWatchmanMatchQuery(EnumSet.noneOf(Capability.class)),
        ImmutableList.of("match", ".idea", "wholename", ImmutableMap.of("includedotfiles", true)));
  }

  @Test
  public void returnsAGlobWhenAskedForPathOrGlob() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertEquals(matcher.getPathOrGlob(), ".idea");
  }
}
