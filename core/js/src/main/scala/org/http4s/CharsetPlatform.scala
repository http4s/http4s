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

package org.http4s

import java.nio.charset.StandardCharsets._

private[http4s] trait CharsetCompanionPlatform {
  private[http4s] def availableCharsets =
    List(US_ASCII, ISO_8859_1, UTF_8, UTF_16, UTF_16BE, UTF_16LE)
}
