/*
 * Copyright (C) 2026 The Flogger Authors.
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

package com.google.common.flogger.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link StaticMethodCaller#getInstanceFromSystemProperty}. */
@RunWith(JUnit4.class)
public final class StaticMethodCallerTest {

  private interface TestService {}

  // A service which only has a public no-arg constructor and no getInstance() method.
  public static final class ConstructorOnlyService implements TestService {
    static int constructorsCalled = 0;

    public ConstructorOnlyService() {
      constructorsCalled++;
    }
  }

  // A service which has both a public no-arg constructor and a static getInstance() method. The
  // sentinel returned from getInstance() lets tests tell which creation path was taken.
  public static final class GetInstanceService implements TestService {
    static final GetInstanceService SENTINEL = new GetInstanceService();
    static int getInstanceCalls = 0;

    public GetInstanceService() {}

    public static GetInstanceService getInstance() {
      getInstanceCalls++;
      return SENTINEL;
    }
  }

  private static final String PROPERTY = "flogger.test.service";

  @Test
  public void testConstructorUsedWhenGetInstanceMethodAbsent() {
    System.setProperty(PROPERTY, ConstructorOnlyService.class.getName());
    try {
      TestService service =
          StaticMethodCaller.getInstanceFromSystemProperty(PROPERTY, TestService.class);
      assertThat(service).isInstanceOf(ConstructorOnlyService.class);
      assertThat(ConstructorOnlyService.constructorsCalled).isGreaterThan(0);
    } finally {
      System.clearProperty(PROPERTY);
    }
  }

  @Test
  public void testGetInstanceUsedWhenPresent() {
    System.setProperty(PROPERTY, GetInstanceService.class.getName());
    try {
      TestService service =
          StaticMethodCaller.getInstanceFromSystemProperty(PROPERTY, TestService.class);
      assertThat(service).isSameInstanceAs(GetInstanceService.SENTINEL);
      assertThat(GetInstanceService.getInstanceCalls).isEqualTo(1);
    } finally {
      System.clearProperty(PROPERTY);
    }
  }

  @Test
  public void testExplicitGetInstanceFallsBackToConstructor() {
    System.setProperty(PROPERTY, ConstructorOnlyService.class.getName() + "#getInstance");
    try {
      TestService service =
          StaticMethodCaller.getInstanceFromSystemProperty(PROPERTY, TestService.class);
      assertThat(service).isInstanceOf(ConstructorOnlyService.class);
      assertThat(ConstructorOnlyService.constructorsCalled).isGreaterThan(0);
    } finally {
      System.clearProperty(PROPERTY);
    }
  }

  @Test
  public void testMissingExplicitMethodReturnsNull() {
    System.setProperty(PROPERTY, GetInstanceService.class.getName() + "#bogusMethod");
    try {
      TestService service =
          StaticMethodCaller.getInstanceFromSystemProperty(PROPERTY, TestService.class);
      assertThat(service).isNull();
    } finally {
      System.clearProperty(PROPERTY);
    }
  }
}
