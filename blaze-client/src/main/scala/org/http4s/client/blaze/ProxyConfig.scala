package org.http4s
package client
package blaze

import org.http4s.Uri.Authority
import org.http4s.util.CaseInsensitiveString

final case class ProxyConfig(
  scheme: CaseInsensitiveString,
  proxyHost: Uri.Host,
  proxyPort: Int,
  credentials: Option[Credentials]
) {
  val authority = Authority(host = proxyHost, port = Some(proxyPort))
}
