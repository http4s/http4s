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

import java.security.MessageDigest

private[authentication] object DigestUtil {
  private def bytes2hex(bytes: Array[Byte]): String = bytes.map("%02x".format(_)).mkString

  private def md5(str: String): String =
    bytes2hex(MessageDigest.getInstance("MD5").digest(str.getBytes))

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
  def computeResponse(
      method: String,
      username: String,
      realm: String,
      password: String,
      uri: String,
      nonce: String,
      nc: String,
      cnonce: String,
      qop: String): String = {
    val ha1str = username + ":" + realm + ":" + password
    val ha1 = md5(ha1str)
    val ha2str = method + ":" + uri
    val ha2 = md5(ha2str)
    val respstr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2
    md5(respstr)
  }
}
