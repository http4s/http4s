package org.http4s.optics

import monocle.Prism
import org.http4s.Method

object method {
  val DELETE: Prism[Method, Unit] = Prism.only(Method.DELETE)
  val GET: Prism[Method, Unit] = Prism.only(Method.GET)
  val PUT: Prism[Method, Unit] = Prism.only(Method.PUT)
  val POST: Prism[Method, Unit] = Prism.only(Method.POST)
}
