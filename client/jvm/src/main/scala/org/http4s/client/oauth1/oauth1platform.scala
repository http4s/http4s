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
package client
package oauth1

import javax.crypto
import java.nio.charset.StandardCharsets

private[oauth1] trait oauth1platform {
  private val SHA1 = "HmacSHA1"
  private def UTF_8 = StandardCharsets.UTF_8

  private[oauth1] def makeSHASigPlatform(
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String]): String = {
    val sha1 = crypto.Mac.getInstance(SHA1)
    val key = encode(consumerSecret) + "&" + tokenSecret.map(t => encode(t)).getOrElse("")
    sha1.init(new crypto.spec.SecretKeySpec(bytes(key), SHA1))

    val sigBytes = sha1.doFinal(bytes(baseString))
    java.util.Base64.getEncoder.encodeToString(sigBytes)
  }

  private def bytes(str: String) = str.getBytes(UTF_8)
}
