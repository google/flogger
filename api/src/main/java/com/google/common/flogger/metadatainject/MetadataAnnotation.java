/*
 * Copyright (C) 2020 The Flogger Authors.
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

package com.google.common.flogger.metadatainject;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Meta annotation for injecting extra {@link MetadataKey}s into log statements.
 *
 * <p>Annotation annotated with this annotations are required to have a public static field that
 * should be of type {@code MetadataKey<T>} and a {@code value()} method returning the same
 * {@code T}.
 *
 * @see com.google.common.flogger.android.AndroidLogTag
 */
@Target(ANNOTATION_TYPE)
@Retention(CLASS)
public @interface MetadataAnnotation {}
