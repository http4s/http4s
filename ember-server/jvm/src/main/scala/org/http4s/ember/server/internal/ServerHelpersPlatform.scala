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

import cats.syntax.all._
import org.http4s.internal.tls.deduceKeyLength
import org.http4s.internal.tls.getCertChain
import org.http4s.server.SecureSession
import scodec.bits.ByteVector

import javax.net.ssl.SSLSession

private[internal] trait ServerHelpersPlatform {

  def parseSSLSession(session: SSLSession): Option[SecureSession] =
    (
      Option(session.getId).map(ByteVector.view(_).toHex),
      Option(session.getCipherSuite),
      Option(session.getCipherSuite).map(deduceKeyLength),
      Some(getCertChain(session)),
    ).mapN(SecureSession.apply)

}
