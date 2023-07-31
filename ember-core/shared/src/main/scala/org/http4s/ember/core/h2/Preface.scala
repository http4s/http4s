/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core.h2

import scodec.bits._

import java.nio.charset.StandardCharsets
// Preface must always be sent an happens before anything else.
private[h2] object Preface {
  val client = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
  // This sequence MUST be followed by a SETTINGS frame
  val clientBV: ByteVector = ByteVector.view(client.getBytes(StandardCharsets.ISO_8859_1))

  // The server connection preface consists of a potentially empty
  // SETTINGS frame (Section 6.5) that MUST be the first frame the server
  // sends in the HTTP/2 connection.
}
