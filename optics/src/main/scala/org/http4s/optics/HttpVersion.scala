package org.http4s.optics

import monocle.Prism
import org.http4s.HttpVersion

object httpversion {
  val `HTTP/1.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.0`)
  val `HTTP/1.1`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.1`)
  val `HTTP/2.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/2.0`)
}
