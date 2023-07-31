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

import java.security.cert.X509Certificate

final case class SecureSession(
    sslSessionId: String,
    cipherSuite: String,
    keySize: Int,
    X509Certificate: List[X509Certificate],
)

object SecureSession {
  def apply(
      sslSessionId: String,
      cipherSuite: String,
      keySize: Int,
      X509Certificate: Array[X509Certificate],
  ): SecureSession =
    SecureSession(sslSessionId, cipherSuite, keySize, X509Certificate.toList)
}
