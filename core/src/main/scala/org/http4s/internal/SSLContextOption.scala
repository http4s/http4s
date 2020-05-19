/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal

import javax.net.ssl.SSLContext
import scala.util.control.NonFatal

/**
  * Indicates how to resolve SSLContext.
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
