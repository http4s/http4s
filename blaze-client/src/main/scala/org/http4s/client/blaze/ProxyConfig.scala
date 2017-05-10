package org.http4s
package client
package blaze

import org.http4s.Uri.Authority
import org.http4s.util.CaseInsensitiveString

case class ProxyConfig(
  scheme: CaseInsensitiveString,
  authority: Authority,
  credentials: Option[Credentials]
)
