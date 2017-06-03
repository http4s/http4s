package org.http4s
package server
package jetty

import cats.effect.IO

class JettyServerSpec extends ServerAddressSpec {
  val builder = JettyBuilder[IO]
}
