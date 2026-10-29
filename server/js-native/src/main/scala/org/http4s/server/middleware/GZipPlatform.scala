/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware

import fs2.compression.DeflateParams
import scodec.bits.ByteVector

private[middleware] object GZipPlatform {

  /** No one-pass compressor on this platform: strict bodies use the streaming path. */
  def gzipStrict(bytes: ByteVector, level: DeflateParams.Level): Option[ByteVector] = {
    val _ = (bytes, level)
    None
  }
}
