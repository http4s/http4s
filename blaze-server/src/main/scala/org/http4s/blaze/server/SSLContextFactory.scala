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

package org.http4s.blaze.server

import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

@deprecated("Moved to org.http4s.internal.tls", "0.21.19")
private[http4s] object SSLContextFactory {
  def getCertChain(sslSession: SSLSession): List[X509Certificate] =
    org.http4s.internal.tls.getCertChain(sslSession)

  def deduceKeyLength(cipherSuite: String): Int =
    org.http4s.internal.tls.deduceKeyLength(cipherSuite)
}
