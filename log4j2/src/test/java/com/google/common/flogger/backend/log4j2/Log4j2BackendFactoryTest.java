/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger.backend.log4j2;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.backend.system.BackendFactory;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests specific to the {@link Log4j2BackendFactory} implementation. */
@RunWith(JUnit4.class)
public final class Log4j2BackendFactoryTest {

  @Test
  public void testCanLoadWithServiceLoader() {
    ImmutableList<BackendFactory> factories =
        ImmutableList.copyOf(ServiceLoader.load(BackendFactory.class));
    assertThat(factories).hasSize(1);
    assertThat(factories.get(0)).isInstanceOf(Log4j2BackendFactory.class);
  }
}
