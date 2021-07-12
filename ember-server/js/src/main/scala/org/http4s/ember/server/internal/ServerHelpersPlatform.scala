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

package org.http4s.ember.server.internal

import org.http4s.server.SecureSession
import fs2.Chunk
import scodec.bits.ByteVector

private[internal] trait ServerHelpersPlatform {

  private[internal] def parseSSLSession(session: Chunk[Byte]): Option[SecureSession] =
    Some(SecureSession(ByteVector(session.toByteBuffer)))

}
