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

  def computeHashedResponse[F[_]: Monad: Hash](
      method: String,
      ha1: String,
      uri: Uri,
      nonce: String,
      nc: String,
      cnonce: String,
      qop: String,
  ): F[String] = computeHashedResponse[F](
    method,
    ha1,
    uri.toString(),
    nonce,
    nc,
    cnonce,
    qop,
  )

  /** Computes the response value used in Digest Authentication.
    * @param method
    * @param ha1
    * @param uri
    * @param nonce
    * @param nc
    * @param cnonce
    * @param qop
    * @return
    */
  def computeHashedResponse[F[_]: Monad: Hash](
      method: String,
      ha1: String,
      uri: String,
      nonce: String,
      nc: String,
      cnonce: String,
      qop: String,
  ): F[String] = for {
    ha2str <- (method + ":" + uri).pure[F]
    ha2 <- md5(ha2str)
    respstr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2
    result <- md5(respstr)
  } yield result

  /** Computes the ha1 component of the Digest Authentication scheme
    * @param username
    * @param realm
    * @param password
    * @return The hash of the supplied fields when concatenated together
    */
  def computeHa1[F[_]: Monad: Hash](username: String, realm: String, password: String): F[String] =
    for {
      ha1str <- (username + ":" + realm + ":" + password).pure[F]
      ha1 <- md5(ha1str)
    } yield ha1

  def computeResponse[F[_]: Monad: Hash](
      method: String,
      username: String,
      realm: String,
      password: String,
      uri: Uri,
      nonce: String,
      nc: String,
      cnonce: String,
      qop: String,
  ): F[String] = computeResponse[F](
    method,
    username,
    realm,
    password,
    uri.toString(),
    nonce,
    nc,
    cnonce,
    qop,
  )

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
    ha1 <- computeHa1(username, realm, password)
    result <- computeHashedResponse(method, ha1, uri, nonce, nc, cnonce, qop)
  } yield result
}
