/*
 * Copyright 2013 http4s.org
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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
 * Copyright (c) 2011 Mojolly Ltd.
 * See licenses/LICENSE_rl
 */

package org.http4s.util

import org.http4s.internal.CharPredicate

private[http4s] object UrlCodingUtils {
  val GenDelims: CharPredicate =
    CharPredicate.from(":/?#[]@".toSet)

  val SubDelims: CharPredicate =
    CharPredicate.from("!$&'()*+,;=".toSet)
}
