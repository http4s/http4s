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

package org.http4s
package server
package middleware
package authentication

import cats.Monad
import cats.syntax.all._
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import scodec.bits.ByteVector

private[authentication] object DigestUtil {

  private def md5[F[_]: Monad: Hash](str: String): F[String] =
    Hash[F].digest(HashAlgorithm.MD5, ByteVector.view(str.getBytes())).map(_.toHex)

  /** Computes the response value used in Digest Authentication.
    * @param method
    * @param username
    * @param realm
    * @param password
    * @param uri
    * @param nonce
    * @param nc
    * @param cnonce
    * @param qop
    * @return
    */
  def computeResponse[F[_]: Monad: Hash](
      method: String,
      username: String,
      realm: String,
      password: String,
      uri: String,
      nonce: String,
      nc: String,
      cnonce: String,
      qop: String,
  ): F[String] = for {
    ha1str <- (username + ":" + realm + ":" + password).pure[F]
    ha1 <- md5(ha1str)
    ha2str = method + ":" + uri
    ha2 <- md5(ha2str)
    respstr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2
    result <- md5(respstr)
  } yield result
}
