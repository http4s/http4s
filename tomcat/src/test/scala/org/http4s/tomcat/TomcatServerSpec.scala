package org.http4s
package server
package tomcat

import cats.effect._

class TomcatServerSpec extends ServerAddressSpec {
  val builder = TomcatBuilder[IO]
}
