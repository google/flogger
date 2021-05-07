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
import static org.junit.Assert.fail;

import com.google.common.flogger.context.ScopedLoggingContext.InvalidLoggingContextStateException;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// Implementation is tested via subclasses of AbstractScopedLoggingContextTest.
@RunWith(JUnit4.class)
public class ScopedLoggingContextTest {
  // A context which fails when the scope is closed. Used to verify that user errors are
  // prioritized in cases where errors cause scopes to be exited.
  private static final ScopedLoggingContext ERROR_CONTEXT =
      new ScopedLoggingContext() {
        @Override
        public ScopedLoggingContext.Builder newContext() {
          return new ScopedLoggingContext.Builder() {
            @Override
            public LoggingContextCloseable install() {
              return () -> {
                throw new IllegalArgumentException("BAD CONTEXT");
              };
            }
          };
        }

        @Override
        public Builder newContext(ScopeType scopeType) {
          return newContext();
        }

        @Override
        public boolean applyLogLevelMap(LogLevelMap m) {
          return false;
        }

        @Override
        public boolean addTags(Tags tags) {
          return false;
        }
      };

  @Test
  public void testErrorHandlingWithoutUserError() {
    InvalidLoggingContextStateException e =
        assertThrows(
            InvalidLoggingContextStateException.class,
            () -> ERROR_CONTEXT.newContext().run(() -> {}));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("BAD CONTEXT");
  }

  @Test
  public void testErrorHandlingWithUserError() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ERROR_CONTEXT
                    .newContext()
                    .run(
                        () -> {
                          throw new IllegalArgumentException("User error");
                        }));
    assertThat(e).hasMessageThat().isEqualTo("User error");
  }

  // Annoyingly Bazel does not support a sufficiently recent version of JUnit so we have to
  // reimplement assertThrows() ourselves.
  private static <T extends Throwable> T assertThrows(Class<T> clazz, Runnable code) {
    try {
      code.run();
    } catch (Throwable t) {
      if (!clazz.isInstance(t)) {
        fail("expected " + clazz.getName() + " but got " + t.getClass().getName());
      }
      return clazz.cast(t);
    }
    fail("expected " + clazz.getName() + " was not thrown");
    // Unreachable code to keep the compiler happy.
    return null;
  }
}
