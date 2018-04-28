package org.http4s
package server
package blaze

import cats.effect.IO

class BlazeServerSpec extends ServerSpec {
  def builder = BlazeBuilder[IO]
}
