/*
 * Copyright (C) 2019 The Flogger Authors.
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

package com.google.common.flogger.context;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogLevelMapTest {

  // We have a different implementation for empty maps (ie, just changing the global log level).
  @Test
  public void testGetLevel_empty() {
    LogLevelMap levelMap = LogLevelMap.create(ImmutableMap.<String, Level>of(), Level.WARNING);

    assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.WARNING);
  }

  // We have a different implementation for singleton maps.
  @Test
  public void testGetLevel_single() {
    LogLevelMap levelMap =
        LogLevelMap.create(ImmutableMap.of("com.example", Level.FINE), Level.WARNING);

    assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.FINE);
    assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.FINE);
  }

  // General implementation.
  @Test
  public void testGetLevel_general() {
    ImmutableMap<String, Level> map =
        ImmutableMap.of(
            "com.example.foo", Level.INFO,
            "com.example.foobar", Level.FINE,
            "com.example.foo.bar", Level.FINER);
    LogLevelMap levelMap = LogLevelMap.create(map, Level.WARNING);

    assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO);
    assertThat(levelMap.getLevel("com.example.foo.foo")).isEqualTo(Level.INFO);
    assertThat(levelMap.getLevel("com.example.foo.foo.foo.foo")).isEqualTo(Level.INFO);
    assertThat(levelMap.getLevel("com.example.foobar")).isEqualTo(Level.FINE);
    assertThat(levelMap.getLevel("com.example.foo.bar")).isEqualTo(Level.FINER);
  }

  @Test
  public void testLevelImmutable() {
    Map<String, Level> mutableMap = new HashMap<>();
    mutableMap.put("com.example", Level.INFO);
    LogLevelMap levelMap = LogLevelMap.create(mutableMap, Level.WARNING);
    assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO);

    // Changing the mutable map has no effect after creating the level map.
    mutableMap.put("com.example.foo", Level.FINE);
    assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO);
  }

  @Test
  public void testBuilder() {
    LogLevelMap levelMap =
        LogLevelMap.builder()
            .add(Level.FINE, String.class)
            .add(Level.WARNING, String.class.getPackage())
            .setDefault(Level.INFO)
            .build();
    assertThat(levelMap.getLevel("com.google")).isEqualTo(Level.INFO);
    assertThat(levelMap.getLevel("java.lang")).isEqualTo(Level.WARNING);
    assertThat(levelMap.getLevel("java.lang.String")).isEqualTo(Level.FINE);
  }
}
