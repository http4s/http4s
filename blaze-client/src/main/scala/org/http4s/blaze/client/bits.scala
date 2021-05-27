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

package org.http4s.blaze.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager}
import org.http4s.{BuildInfo, ProductId}
import org.http4s.headers.`User-Agent`
import scala.concurrent.duration._

private[http4s] object bits {
  // Some default objects
  val DefaultResponseHeaderTimeout: Duration = 10.seconds
  val DefaultTimeout: Duration = 60.seconds
  val DefaultBufferSize: Int = 8 * 1024
  val DefaultUserAgent = Some(`User-Agent`(ProductId("http4s-blaze", Some(BuildInfo.version))))
  val DefaultMaxTotalConnections = 10
  val DefaultMaxWaitQueueLimit = 256

  /** Caution: trusts all certificates and disables endpoint identification */
  lazy val TrustingSslContext: SSLContext = {
    val trustManager = new X509TrustManager {
      def getAcceptedIssuers(): Array[X509Certificate] = Array.empty
      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(trustManager), new SecureRandom)
    sslContext
  }
}
