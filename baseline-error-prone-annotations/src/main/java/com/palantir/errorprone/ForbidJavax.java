/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.errorprone;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation applied to any method parameter which requires that all input types
 * include at least one Jakarta EE 9 type, such as <code>jakarta.ws.rs.Path</code>
 * or <code>jakarta.ws.rs.core.Feature</code> instead of the legacy <code>javax</code>
 * versions of those.
 *
 * This sprinkling is required since many methods take a simple <code>Object</code> type
 * and let Jersey determine what to do with the object at runtime. In order to obtain better
 * compile time checking, this method can be applied.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface ForbidJavax {}
