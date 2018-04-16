/*
 * Copyright (C) 2016 The Flogger Authors.
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

package com.google.common.flogger;

/**
 * A tagging interface to mark implementations that are suitable for use as a key for looking up
 * per log site persistent state. Normally the class used is just {@link LogSite} but other, more
 * specific, keys can be used. There are no method requirements on this interface, but the instance
 * must have correct {@code equals()}, {@code hashCode()} and {@code toString()} implementations
 * and must be at least as unique as the associated {@code LogSite} (i.e. two keys created for
 * different log sites must never be equal).
 */
public interface LogSiteKey {}

