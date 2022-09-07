/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import javax.net.ssl.SSLContext
import scala.util.control.NonFatal

/** Indicates how to resolve SSLContext.
  *  * NoSSL                = do not use SSL/HTTPS
  *  * TryDefaultSSLContext = `SSLContext.getDefault()`, or `None` on systems where the default is unavailable
  *  * Provided             = use the explicitly passed SSLContext
  */
private[http4s] sealed trait SSLContextOption extends Product with Serializable
private[http4s] object SSLContextOption {
  case object NoSSL extends SSLContextOption
  case object TryDefaultSSLContext extends SSLContextOption
  final case class Provided(sslContext: SSLContext) extends SSLContextOption

  def toMaybeSSLContext(sco: SSLContextOption): Option[SSLContext] =
    sco match {
      case SSLContextOption.NoSSL => None
      case SSLContextOption.TryDefaultSSLContext => tryDefaultSslContext
      case SSLContextOption.Provided(context) => Some(context)
    }

  def tryDefaultSslContext: Option[SSLContext] =
    try Some(SSLContext.getDefault())
    catch {
      case NonFatal(_) => None
    }
}
