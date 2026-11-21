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

package org.http4s.server

import scodec.bits.ByteVector

/** The TLS session of a connection.
  *
  * @param raw the session, as serialized by the TLS implementation
  * @param peerCertificates the peer's verified certificate chain, DER-encoded,
  * leaf first and including the trusted root. Empty if the peer presented no
  * certificate, or it was not verified. Unlike `X509Certificate` on the JVM,
  * which is the chain as the peer sent it, this is the chain as verified.
  */
final class SecureSession private[http4s] (
    val raw: ByteVector,
    val peerCertificates: List[ByteVector],
) {
  @deprecated("Preserved for binary compatibility", "0.23.40")
  private[http4s] def this(raw: ByteVector) = this(raw, Nil)
}
